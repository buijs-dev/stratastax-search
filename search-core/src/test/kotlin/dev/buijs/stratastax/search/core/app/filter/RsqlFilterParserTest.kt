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

import dev.buijs.stratastax.search.core.api.AndExpression
import dev.buijs.stratastax.search.core.api.ComparisonExpression
import dev.buijs.stratastax.search.core.api.ComparisonOperator
import dev.buijs.stratastax.search.core.api.FilterValue
import dev.buijs.stratastax.search.core.api.InExpression
import dev.buijs.stratastax.search.core.api.InOperator
import dev.buijs.stratastax.search.core.api.LikeExpression
import dev.buijs.stratastax.search.core.api.OrExpression
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.playedAtField
import dev.buijs.stratastax.search.testdata.statusField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class RsqlFilterParserTest {

    private val parser = RsqlFilterParser()

    @Nested
    inner class HappyPathTests {

        @Test
        fun `should return null for null or blank input`() {
            assertThat(parser.parse(null, testSearchConfiguration)).isNull()
            assertThat(parser.parse("", testSearchConfiguration)).isNull()
            assertThat(parser.parse("   ", testSearchConfiguration)).isNull()
        }

        @Test
        fun `should parse simple equality and ignore field name casing`() {
            val filter = parser.parse("PLAYEDAT==2026-01-01T00:00:00Z", testSearchConfiguration)
            assertThat(filter).isNotNull()

            val expected =
                ComparisonExpression(
                    field = playedAtField,
                    operator = ComparisonOperator.EQUALS,
                    value = FilterValue.DateTimeValue(Instant.parse("2026-01-01T00:00:00Z")),
                )
            assertThat(filter!!.root).isEqualTo(expected)
        }

        @Test
        fun `should parse IN operator list`() {
            val filter = parser.parse("status=in=(PENDING,ACTIVE)", testSearchConfiguration)
            assertThat(filter).isNotNull()

            val expected =
                InExpression(
                    field = statusField,
                    operator = InOperator.IN,
                    values =
                        listOf(FilterValue.EnumValue("PENDING"), FilterValue.EnumValue("ACTIVE")),
                )
            assertThat(filter!!.root).isEqualTo(expected)
        }

        @Test
        fun `should parse LIKE operator with wildcards`() {
            val filter = parser.parse("name=like=\"Alex*\"", testSearchConfiguration)
            assertThat(filter).isNotNull()

            val expected = LikeExpression(field = nameField, pattern = "Alex*")
            assertThat(filter!!.root).isEqualTo(expected)
        }

        @Test
        fun `should parse ILIKE operator as a case-insensitive LikeExpression`() {
            val filter = parser.parse("name=ilike=\"%alex%\"", testSearchConfiguration)

            val expected = LikeExpression(field = nameField, pattern = "%alex%", ignoreCase = true)
            assertThat(filter!!.root).isEqualTo(expected)
        }

        @Test
        fun `should parse AND with higher precedence than OR`() {
            // playedAt=gt=2026-01-01T00:00:00Z;status==COMPLETED,score=gt=100
            // Equivalent to: (playedAt > 2026 AND status == COMPLETED) OR
            // (score > 100)
            val filter =
                parser.parse(
                    "playedAt=gt=2026-01-01T00:00:00Z;status==COMPLETED,score=gt=100",
                    testSearchConfiguration,
                )
            assertThat(filter).isNotNull()

            assertThat(filter!!.root).isInstanceOf(OrExpression::class.java)
            val orExpr = filter.root as OrExpression
            assertThat(orExpr.children.size).isEqualTo(2)

            assertThat(orExpr.children[0]).isInstanceOf(AndExpression::class.java)
            assertThat(orExpr.children[1]).isInstanceOf(ComparisonExpression::class.java)
        }

        @Test
        fun `should respect explicit parenthesis grouping`() {
            // playedAt=ge=2026-01-01T00:00:00Z;(status==COMPLETED,score=gt=100)
            val filter =
                parser.parse(
                    "playedAt=ge=2026-01-01T00:00:00Z;(status==COMPLETED,score=gt=100)",
                    testSearchConfiguration,
                )
            assertThat(filter).isNotNull()

            assertThat(filter!!.root).isInstanceOf(AndExpression::class.java)
            val andExpr = filter.root as AndExpression
            assertThat(andExpr.children.size).isEqualTo(2)

            assertThat(andExpr.children[1]).isInstanceOf(OrExpression::class.java)
        }
    }

    @Nested
    inner class NullKeywordTests {

        private fun parseRoot(query: String) = parser.parse(query, testSearchConfiguration)!!.root

        @Test
        fun `unquoted null is the null keyword`() {
            assertThat(parseRoot("name==null"))
                .isEqualTo(
                    ComparisonExpression(
                        nameField,
                        ComparisonOperator.EQUALS,
                        FilterValue.NullValue,
                    ),
                )
        }

        @Test
        fun `unquoted NULL is the null keyword regardless of casing`() {
            assertThat(parseRoot("name!=NULL"))
                .isEqualTo(
                    ComparisonExpression(
                        nameField,
                        ComparisonOperator.NOT_EQUALS,
                        FilterValue.NullValue,
                    ),
                )
        }

        @Test
        fun `double quoted null is the text null`() {
            assertThat(parseRoot("name==\"null\""))
                .isEqualTo(
                    ComparisonExpression(
                        nameField,
                        ComparisonOperator.EQUALS,
                        FilterValue.StringValue("null"),
                    ),
                )
        }

        @Test
        fun `single quoted NULL is the text NULL`() {
            assertThat(parseRoot("name!='NULL'"))
                .isEqualTo(
                    ComparisonExpression(
                        nameField,
                        ComparisonOperator.NOT_EQUALS,
                        FilterValue.StringValue("NULL"),
                    ),
                )
        }

        @Test
        fun `quoted null inside a list is the text null`() {
            assertThat(parseRoot("name=in=(\"null\",x)"))
                .isEqualTo(
                    InExpression(
                        nameField,
                        InOperator.IN,
                        listOf(FilterValue.StringValue("null"), FilterValue.StringValue("x")),
                    ),
                )
        }

        @Test
        fun `unquoted null inside a list is still rejected`() {
            assertThatThrownBy { parser.parse("name=in=(null,x)", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("NULL is only supported")
        }

        @Test
        fun `quoted null on a number field is an invalid number, not a null`() {
            assertThatThrownBy { parser.parse("score==\"null\"", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("Invalid number")
        }
    }

    @Nested
    inner class UnquotedStringTests {

        @Test
        fun `unquoted value may contain equals and exclamation signs`() {
            val filter = parser.parse("name==a=b!c", testSearchConfiguration)

            assertThat((filter!!.root as ComparisonExpression).value)
                .isEqualTo(FilterValue.StringValue("a=b!c"))
        }
    }

    @Nested
    inner class DateTimeFormatTests {

        @Test
        fun `date without time is parsed as LocalDate`() {
            val filter = parser.parse("playedAt=ge=2026-01-01", testSearchConfiguration)

            assertThat((filter!!.root as ComparisonExpression).value)
                .isEqualTo(FilterValue.DateValue(LocalDate.of(2026, 1, 1)))
        }

        @Test
        fun `date-time without offset is rejected`() {
            assertThatThrownBy {
                parser.parse("playedAt==2026-01-01T00:00:00", testSearchConfiguration)
            }.isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `utc instant with fractional seconds is accepted`() {
            val filter = parser.parse("playedAt==2026-01-01T00:00:00.500Z", testSearchConfiguration)

            assertThat((filter!!.root as ComparisonExpression).value)
                .isEqualTo(FilterValue.DateTimeValue(Instant.parse("2026-01-01T00:00:00.500Z")))
        }

        @Test
        fun `non-utc offset is accepted and normalized to the same instant`() {
            val filter =
                parser.parse("playedAt==2026-01-01T02:00:00+02:00", testSearchConfiguration)

            assertThat((filter!!.root as ComparisonExpression).value)
                .isEqualTo(FilterValue.DateTimeValue(Instant.parse("2026-01-01T00:00:00Z")))
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `should throw when filtering on non-filterable field`() {
            assertThatThrownBy { parser.parse("internalNotes==secret", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("not filterable")
        }

        @Test
        fun `should throw when using comparison operator gt on non-numeric or non-datetime field`() {
            assertThatThrownBy { parser.parse("name=gt=abc", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("not supported")
        }

        @Test
        fun `should throw when using like operator on non-string field`() {
            assertThatThrownBy { parser.parse("score=like=\"100*\"", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("is not supported")
        }

        @Test
        fun `should throw on malformed ISO-8601 date`() {
            assertThatThrownBy { parser.parse("playedAt==2026-99-99", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
        }
    }
}
