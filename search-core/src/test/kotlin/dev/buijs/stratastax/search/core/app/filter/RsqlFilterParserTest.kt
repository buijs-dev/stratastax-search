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
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        fun `should throw on malformed ISO-8601 UTC date`() {
            assertThatThrownBy { parser.parse("playedAt==2026-99-99", testSearchConfiguration) }
                .isInstanceOf(SearchException::class.java)
        }
    }
}
