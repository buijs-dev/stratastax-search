/**
 * Copyright (c) 2021 - 2026 Buijs Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package dev.buijs.stratastax.search.core.app.filter

import dev.buijs.stratastax.search.core.api.SearchException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class TokenizerTest {

    private fun tokenize(input: String): List<Token> = Tokenizer(input).tokenize()

    private fun field(name: String) = Token(TokenType.FIELD, name)

    private fun operator(op: String) = Token(TokenType.OPERATOR, op)

    private fun value(text: String) = Token(TokenType.VALUE, text)

    private fun quoted(text: String) = Token(TokenType.VALUE, text, quoted = true)

    private fun and(raw: String = ";") = Token(TokenType.AND, raw)

    private fun or(raw: String = ",") = Token(TokenType.OR, raw)

    private val lparen = Token(TokenType.LPAREN, "(")
    private val rparen = Token(TokenType.RPAREN, ")")

    @Nested
    @DisplayName("Basic comparisons")
    inner class BasicComparisons {

        @Test
        fun `simple equals`() {
            assertThat(tokenize("name==Alice"))
                .isEqualTo(listOf(field("name"), operator("=="), value("Alice")))
        }

        @Test
        fun `not equals`() {
            assertThat(tokenize("status!=closed"))
                .isEqualTo(listOf(field("status"), operator("!="), value("closed")))
        }

        @ParameterizedTest(name = "{0} -> operator {1}")
        @CsvSource(
            "age=gt=5,      =gt=",
            "age=ge=5,      =ge=",
            "age=lt=5,      =lt=",
            "age=le=5,      =le=",
            "name=like=Al,  =like=",
        )
        fun `range and like operators`(
            input: String,
            expectedOp: String,
        ) {
            val tokens = tokenize(input)
            assertThat(tokens[1].type).isEqualTo(TokenType.OPERATOR)
            assertThat(tokens[1].text).isEqualTo(expectedOp)
        }

        @Test
        fun `whitespace around tokens is ignored`() {
            assertThat(tokenize("  name  ==   Alice  "))
                .isEqualTo(listOf(field("name"), operator("=="), value("Alice")))
        }

        @Test
        fun `no whitespace between chained predicates`() {
            assertThat(tokenize("a==1;b==2"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        and(";"),
                        field("b"),
                        operator("=="),
                        value("2"),
                    ),
                )
        }
    }

    @Nested
    @DisplayName("Case-insensitive operators")
    inner class CaseInsensitiveOperators {

        @ParameterizedTest
        @CsvSource(
            "age=GT=5,      =gt=",
            "age=Gt=5,      =gt=",
            "name=LIKE=Al,  =like=",
            "status=IN=(a), =in=",
        )
        fun `operator casing normalises to canonical lowercase`(
            input: String,
            expectedOp: String,
        ) {
            assertThat(tokenize(input)[1].text).isEqualTo(expectedOp)
        }
    }

    @Nested
    @DisplayName("Logical operators - symbols and keywords")
    inner class LogicalOperators {

        @Test
        fun `semicolon is AND`() {
            assertThat(tokenize("a==1;b==2"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        and(";"),
                        field("b"),
                        operator("=="),
                        value("2"),
                    ),
                )
        }

        @Test
        fun `comma outside a list is OR`() {
            assertThat(tokenize("a==1,b==2"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        or(","),
                        field("b"),
                        operator("=="),
                        value("2"),
                    ),
                )
        }

        @Test
        fun `textual and keyword`() {
            assertThat(tokenize("a==1 and b==2"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        and("and"),
                        field("b"),
                        operator("=="),
                        value("2"),
                    ),
                )
        }

        @Test
        fun `textual or keyword`() {
            assertThat(tokenize("a==1 or b==2"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        or("or"),
                        field("b"),
                        operator("=="),
                        value("2"),
                    ),
                )
        }

        @ParameterizedTest
        @ValueSource(strings = ["and", "AND", "And", "aNd"])
        fun `and keyword is matched case-insensitively but its raw text is preserved`(kw: String) {
            val tokens = tokenize("a==1 $kw b==2")
            assertThat(tokens[3]).isEqualTo(Token(TokenType.AND, kw))
        }

        @Test
        fun `mixed symbol and keyword connectors`() {
            assertThat(tokenize("a==1;b==2,c==3"))
                .isEqualTo(
                    listOf(
                        field("a"),
                        operator("=="),
                        value("1"),
                        and(";"),
                        field("b"),
                        operator("=="),
                        value("2"),
                        or(","),
                        field("c"),
                        operator("=="),
                        value("3"),
                    ),
                )
        }

        @Test
        fun `a field literally named 'and' is a FIELD, not a keyword`() {
            assertThat(tokenize("and==1"))
                .isEqualTo(listOf(field("and"), operator("=="), value("1")))
        }

        @Test
        fun `a field literally named 'or' is a FIELD, not a keyword`() {
            assertThat(tokenize("or==1")).isEqualTo(listOf(field("or"), operator("=="), value("1")))
        }
    }

    @Nested
    @DisplayName("Grouping parentheses")
    inner class GroupingParens {

        @Test
        fun `simple group`() {
            assertThat(tokenize("(a==1)"))
                .isEqualTo(listOf(lparen, field("a"), operator("=="), value("1"), rparen))
        }

        @Test
        fun `two groups joined by or`() {
            assertThat(tokenize("(a==1) or (b==2)"))
                .isEqualTo(
                    listOf(
                        lparen,
                        field("a"),
                        operator("=="),
                        value("1"),
                        rparen,
                        or("or"),
                        lparen,
                        field("b"),
                        operator("=="),
                        value("2"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `nested groups`() {
            assertThat(tokenize("((a==1) and b==2)"))
                .isEqualTo(
                    listOf(
                        lparen,
                        lparen,
                        field("a"),
                        operator("=="),
                        value("1"),
                        rparen,
                        and("and"),
                        field("b"),
                        operator("=="),
                        value("2"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `no whitespace between closing paren, keyword and opening paren`() {
            assertThat(tokenize("(a==1)and(b==2)"))
                .isEqualTo(
                    listOf(
                        lparen,
                        field("a"),
                        operator("=="),
                        value("1"),
                        rparen,
                        and("and"),
                        lparen,
                        field("b"),
                        operator("=="),
                        value("2"),
                        rparen,
                    ),
                )
        }
    }

    @Nested
    @DisplayName("IN / OUT lists")
    inner class InOutLists {

        @Test
        fun `single value list`() {
            assertThat(tokenize("status=in=(a)"))
                .isEqualTo(listOf(field("status"), operator("=in="), lparen, value("a"), rparen))
        }

        @Test
        fun `multi value list, commas are not tokens`() {
            assertThat(tokenize("status=in=(a,b,c)"))
                .isEqualTo(
                    listOf(
                        field("status"),
                        operator("=in="),
                        lparen,
                        value("a"),
                        value("b"),
                        value("c"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `out operator`() {
            assertThat(tokenize("status=out=(a,b)"))
                .isEqualTo(
                    listOf(
                        field("status"),
                        operator("=out="),
                        lparen,
                        value("a"),
                        value("b"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `quoted values inside list`() {
            assertThat(tokenize("name=in=(\"Alice\",'Bob')"))
                .isEqualTo(
                    listOf(
                        field("name"),
                        operator("=in="),
                        lparen,
                        quoted("Alice"),
                        quoted("Bob"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `empty list tokenizes fine, emptiness is validated by the parser`() {
            assertThat(tokenize("status=in=()"))
                .isEqualTo(listOf(field("status"), operator("=in="), lparen, rparen))
        }

        @Test
        fun `trailing comma before closing paren throws`() {
            assertThatThrownBy { tokenize("status=in=(a,b,)") }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("Trailing comma")
        }

        @Test
        fun `trailing comma in single-element list throws`() {
            assertThatThrownBy { tokenize("status=in=(a,)") }
                .isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `whitespace around list values`() {
            assertThat(tokenize("status=in=( a , b )"))
                .isEqualTo(
                    listOf(
                        field("status"),
                        operator("=in="),
                        lparen,
                        value("a"),
                        value("b"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `connector after a closed list is recognised`() {
            assertThat(tokenize("x=in=(1,2) and y==3"))
                .isEqualTo(
                    listOf(
                        field("x"),
                        operator("=in="),
                        lparen,
                        value("1"),
                        value("2"),
                        rparen,
                        and("and"),
                        field("y"),
                        operator("=="),
                        value("3"),
                    ),
                )
        }

        @Nested
        @DisplayName("'and'/'or' as literal list values")
        inner class KeywordsAsListValues {

            @Test
            fun `unquoted 'and' as a list value stays a VALUE, not an AND token`() {
                assertThat(tokenize("status=in=(and,pending)"))
                    .isEqualTo(
                        listOf(
                            field("status"),
                            operator("=in="),
                            lparen,
                            value("and"),
                            value("pending"),
                            rparen,
                        ),
                    )
            }

            @Test
            fun `unquoted 'or' as the only list value stays a VALUE, not an OR token`() {
                assertThat(tokenize("status=in=(or)"))
                    .isEqualTo(
                        listOf(field("status"), operator("=in="), lparen, value("or"), rparen),
                    )
            }
        }
    }

    @Nested
    @DisplayName("Quoted strings")
    inner class QuotedStrings {

        @Test
        fun `double quoted value`() {
            assertThat(tokenize("name==\"Alice Smith\""))
                .isEqualTo(listOf(field("name"), operator("=="), quoted("Alice Smith")))
        }

        @Test
        fun `single quoted value`() {
            assertThat(tokenize("name=='Alice Smith'"))
                .isEqualTo(listOf(field("name"), operator("=="), quoted("Alice Smith")))
        }

        @Test
        fun `escaped quote inside a quoted value`() {
            // Kotlin literal "name=='it\\'s ok'" is the raw filter string:
            // name=='it\'s ok'
            assertThat(tokenize("name=='it\\'s ok'"))
                .isEqualTo(listOf(field("name"), operator("=="), quoted("it's ok")))
        }

        @Test
        fun `escaped backslash inside a quoted value`() {
            // Kotlin literal "back\\\\slash" is the raw filter content:
            // back\\slash (two backslashes)
            assertThat(tokenize("path==\"back\\\\slash\""))
                .isEqualTo(listOf(field("path"), operator("=="), quoted("back\\slash")))
        }

        @Test
        fun `quoted value containing a connector word stays a value`() {
            assertThat(tokenize("name==\"and\""))
                .isEqualTo(listOf(field("name"), operator("=="), quoted("and")))
        }

        @Test
        fun `quoted null is flagged as quoted, unquoted null is not`() {
            assertThat(tokenize("name==\"null\""))
                .isEqualTo(listOf(field("name"), operator("=="), quoted("null")))
            assertThat(tokenize("name==null"))
                .isEqualTo(listOf(field("name"), operator("=="), value("null")))
        }

        @Test
        fun `unterminated quoted string throws`() {
            assertThatThrownBy { tokenize("name==\"Alice") }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("Unterminated string")
        }
    }

    @Nested
    @DisplayName("and/or ambiguity right after an operator")
    inner class KeywordValueAmbiguity {

        @Test
        fun `unquoted 'and' as a comparison value stays a VALUE`() {
            assertThat(tokenize("status==and"))
                .isEqualTo(listOf(field("status"), operator("=="), value("and")))
        }

        @Test
        fun `unquoted 'or' as a comparison value stays a VALUE`() {
            assertThat(tokenize("status!=or"))
                .isEqualTo(listOf(field("status"), operator("!="), value("or")))
        }

        @Test
        fun `the connector that follows such a value is still recognised`() {
            assertThat(tokenize("status==and and other==1"))
                .isEqualTo(
                    listOf(
                        field("status"),
                        operator("=="),
                        value("and"),
                        and("and"),
                        field("other"),
                        operator("=="),
                        value("1"),
                    ),
                )
        }
    }

    @Nested
    @DisplayName("'=' and '!' allowed inside unquoted values")
    inner class LenientValueCharacters {

        @Test
        fun `value containing an embedded equals sign`() {
            assertThat(tokenize("note==key=value"))
                .isEqualTo(listOf(field("note"), operator("=="), value("key=value")))
        }

        @Test
        fun `value containing an embedded exclamation mark`() {
            assertThat(tokenize("note==a!b"))
                .isEqualTo(listOf(field("note"), operator("=="), value("a!b")))
        }

        @Test
        fun `list values containing embedded equals signs`() {
            assertThat(tokenize("note=in=(k=v,x=y)"))
                .isEqualTo(
                    listOf(
                        field("note"),
                        operator("=in="),
                        lparen,
                        value("k=v"),
                        value("x=y"),
                        rparen,
                    ),
                )
        }

        @Test
        fun `field names still stop at equals, starting the operator as usual`() {
            assertThat(tokenize("field==1"))
                .isEqualTo(listOf(field("field"), operator("=="), value("1")))
        }
    }

    @Nested
    @DisplayName("Malformed input")
    inner class MalformedInput {

        @Test
        fun `unknown operator throws`() {
            assertThatThrownBy { tokenize("field=xyz=5") }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("Unknown or malformed operator")
        }

        @Test
        fun `lone exclamation mark not followed by equals throws`() {
            assertThatThrownBy { tokenize("field!invalid") }
                .isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `unclosed list tokenizes without a trailing RPAREN`() {
            // Balanced-paren validation happens in ExpressionParser, not here.
            assertThat(tokenize("status=in=(a,b"))
                .isEqualTo(
                    listOf(field("status"), operator("=in="), lparen, value("a"), value("b")),
                )
        }

        @Test
        fun `in operator without a following paren does not fail at tokenize time`() {
            // No LPAREN is required by the tokenizer itself; ExpressionParser
            // rejects this when it fails to consume TokenType.LPAREN.
            assertThat(tokenize("status=in=5"))
                .isEqualTo(listOf(field("status"), operator("=in="), value("5")))
        }
    }

    @Nested
    @DisplayName("Empty and blank input")
    inner class EmptyInput {

        @Test
        fun `empty string produces no tokens`() {
            assertThat(tokenize("")).isEqualTo(emptyList<Token>())
        }

        @Test
        fun `whitespace only produces no tokens`() {
            assertThat(tokenize("   \t  ")).isEqualTo(emptyList<Token>())
        }
    }
}
