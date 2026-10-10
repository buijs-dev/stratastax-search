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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import dev.buijs.stratastax.entity.core.api.Sort
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.entity.core.api.SortField
import dev.buijs.stratastax.search.core.api.CriteriaHash
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.spi.StandardSearchConfiguration
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.*

/** Syntax and semantic errors of the RSQL parser, and the filter types the main tests skip. */
class RsqlFilterParserErrorTest {

    private val parser = RsqlFilterParser()

    private fun parse(
        value: String,
        parser: RsqlFilterParser = this.parser,
    ) = parser.parse(value, testSearchConfiguration)

    private fun assertRejected(
        value: String,
        message: String,
    ) {
        assertThatThrownBy { parse(value) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage(message)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value =
            [
                "name                  | Expected operator, but got end of input",
                "name==                | Expected value, but got end of input",
                "==x                   | Expected field name, but got '=='",
                "name==a;              | Expected field name, but got end of input",
                "name==a,              | Expected field name, but got end of input",
                "(name==a              | Expected ')', but got end of input",
                "name==a)              | Unexpected token ')' at end of query",
                "name=in=(a            | Expected ')', but got end of input",
                "name=in=a             | Expected '(', but got 'a'",
                "name=in=()            | IN/OUT list for field 'name' cannot be empty",
                "name=gt=a             | Operator '=gt=' is not supported for data type STRING on field 'name'",
                "name=like=            | Expected value, but got end of input",
                "active=in=(true)      | Operator '=in=' is not supported for data type BOOLEAN on field 'active'",
                "active=out=(true)     | Operator '=out=' is not supported for data type BOOLEAN on field 'active'",
                "score=like=1          | Operator '=like=' is not supported for data type NUMBER on field 'score'",
                "nope==a               | Unknown filter field 'nope'",
                "internalNotes==a      | Field 'internalNotes' is not filterable",
                "score=in=(1,x)        | Invalid number format 'x' for field 'score'",
                "score=in=(1,null)     | NULL is only supported with '==' or '!=' for field 'score'",
                "score=gt=null         | NULL is only supported with '==' or '!=' for field 'score'",
                "active==maybe         | Invalid boolean value 'maybe' for field 'active'. Expected 'true' or 'false'",
                "status==GONE          | Invalid enum value 'GONE' for field 'status'. Allowed values: ACTIVE, PENDING, COMPLETED",
            ],
    )
    fun `invalid filters are rejected with a precise message`(
        query: String,
        message: String,
    ) {
        assertRejected(query, message)
    }

    @Test
    fun `nesting beyond the maximum depth is rejected`() {
        val shallow = RsqlFilterParser(maxNestingDepth = 2)

        assertThat(parse("((name==a))", shallow)).isNotNull
        assertThatThrownBy { parse("(((name==a)))", shallow) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Exceeded maximum filter nesting depth of 2.")
    }

    @Test
    fun `the default maximum nesting depth is 100`() {
        assertThat(RsqlFilterParser.DEFAULT_MAX_NESTING_DEPTH).isEqualTo(100)

        val atLimit = "(".repeat(100) + "name==a" + ")".repeat(100)
        val overLimit = "(".repeat(101) + "name==a" + ")".repeat(101)

        assertThat(parse(atLimit)).isNotNull
        assertThatThrownBy { parse(overLimit) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Exceeded maximum filter nesting depth of 100.")
    }

    @Test
    fun `sibling groups do not add up to a deeper nesting`() {
        val shallow = RsqlFilterParser(maxNestingDepth = 1)

        assertThat(parse("(name==a);(name==b);(name==c)", shallow)).isNotNull
    }

    @Test
    fun `uuid, boolean and json fields can be filtered`() {
        val id = UUID.randomUUID()
        val uuidField = EntityField("id", EntityFieldType.UUID, filterable = true)
        val jsonField = EntityField("payload", EntityFieldType.JSON, filterable = true)
        val configuration =
            StandardSearchConfiguration(
                defaultSort = listOf(SortField(nameField, SortDirection.ASC)),
                fields = listOf(nameField, uuidField, jsonField),
                tiebreakerField = nameField,
            )
        val sort = Sort(configuration.defaultSort)

        val filter = parser.parse("id==$id;payload==\"{\\\"a\\\":1}\"", configuration)!!

        assertThat(FilterWriter.write(filter)).isEqualTo("id==\"$id\";payload==\"{\\\"a\\\":1}\"")
        assertThat(parser.parse(FilterWriter.write(filter), configuration)).isEqualTo(filter)
        assertThat(CriteriaHash.of(sort, filter))
            .isNotEqualTo(CriteriaHash.of(sort, parser.parse("id==$id", configuration)))
        assertThatThrownBy { parser.parse("id=in=(a,b)", configuration) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Invalid UUID format 'a' for field 'id'")
        assertThatThrownBy { parser.parse("payload=in=(a)", configuration) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Operator '=in=' is not supported for data type JSON on field 'payload'")
    }
}
