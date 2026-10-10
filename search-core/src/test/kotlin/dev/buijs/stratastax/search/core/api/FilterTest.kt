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
package dev.buijs.stratastax.search.core.api

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FilterTest {

    private val dummyField = EntityField("age", EntityFieldType.NUMBER)
    private val dummyValue = FilterValue.NumberValue(BigDecimal(25))

    @Nested
    inner class AndExpressionTests {

        @Test
        fun `should create AndExpression when children count is 2 or more`() {
            val child1 = ComparisonExpression(dummyField, ComparisonOperator.EQUALS, dummyValue)
            val child2 =
                ComparisonExpression(dummyField, ComparisonOperator.GREATER_THAN, dummyValue)

            val andExpr = AndExpression(listOf(child1, child2))

            assertThat(andExpr.children.size).isEqualTo(2)
        }

        @Test
        fun `should throw FilterException when AndExpression has fewer than 2 children`() {
            val child = ComparisonExpression(dummyField, ComparisonOperator.EQUALS, dummyValue)

            assertThatThrownBy { AndExpression(listOf(child)) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("AndExpression requires at least 2 child expressions.")
        }
    }

    @Nested
    inner class OrExpressionTests {

        @Test
        fun `should create OrExpression when children count is 2 or more`() {
            val child1 = ComparisonExpression(dummyField, ComparisonOperator.EQUALS, dummyValue)
            val child2 = ComparisonExpression(dummyField, ComparisonOperator.LESS_THAN, dummyValue)

            val orExpr = OrExpression(listOf(child1, child2))

            assertThat(orExpr.children.size).isEqualTo(2)
        }

        @Test
        fun `should throw FilterException when OrExpression has fewer than 2 children`() {
            assertThatThrownBy { OrExpression(emptyList()) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("OrExpression requires at least 2 child expressions.")
        }
    }

    @Nested
    inner class InExpressionTests {

        @Test
        fun `should create InExpression with non-empty values`() {
            val expr = InExpression(dummyField, InOperator.IN, listOf(dummyValue))

            assertThat(expr.values.size).isEqualTo(1)
        }

        @Test
        fun `should throw FilterException when InExpression has empty values list`() {
            assertThatThrownBy { InExpression(dummyField, InOperator.IN, emptyList()) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("In/Out expression requires a non-empty list of values.")
        }
    }

    @Nested
    inner class FilterOperatorTests {

        @Test
        fun `FilterOperator companion values should be sorted by length descending`() {
            val tokens = FilterOperator.values
            val isSortedDescendingByLength =
                tokens.zipWithNext().all { (a, b) -> a.length >= b.length }

            assertThat(isSortedDescendingByLength).isTrue()
        }

        @Test
        fun `FilterOperator companion of should correctly resolve operators by token`() {
            assertThat(FilterOperator.of("=gt=")).isEqualTo(ComparisonOperator.GREATER_THAN)
            assertThat(FilterOperator.of("==")).isEqualTo(ComparisonOperator.EQUALS)
            assertThat(FilterOperator.of("=in=")).isEqualTo(InOperator.IN)
            assertThat(FilterOperator.of("=out=")).isEqualTo(InOperator.OUT)
            assertThat(FilterOperator.of("invalid_token")).isNull()
        }

        @Test
        fun `ComparisonOperator allowed types validation`() {
            // GREATER_THAN / GE / LT / LE -> Number & DateTime only
            val includes =
                listOf(EntityFieldType.NUMBER, EntityFieldType.DATE, EntityFieldType.DATETIME)

            EntityFieldType.entries.forEach {
                if (includes.contains(it)) {
                    assertThat(ComparisonOperator.GREATER_THAN.allowed(it)).isTrue()
                } else {
                    assertThat(ComparisonOperator.GREATER_THAN.allowed(it)).isFalse()
                }
            }

            // LIKE -> String only
            EntityFieldType.entries.forEach {
                if (it == EntityFieldType.STRING) {
                    assertThat(ComparisonOperator.LIKE.allowed(it)).isTrue()
                } else {
                    assertThat(ComparisonOperator.LIKE.allowed(it)).isFalse()
                }
            }

            // EQUALS / NOT_EQUALS -> Allowed for all by default
            EntityFieldType.entries.forEach {
                assertThat(ComparisonOperator.EQUALS.allowed(it)).isTrue()
                assertThat(ComparisonOperator.NOT_EQUALS.allowed(it)).isTrue()
            }
        }

        @Test
        fun `InOperator allowed types validation`() {
            // Given IN / OUT -> Allowed for all except BOOLEAN, DATE_TIME and
            // JSON (whole-value equality only - see EntityFieldType.JSON's own
            // kdoc)
            val excludes =
                listOf(
                    EntityFieldType.BOOLEAN,
                    EntityFieldType.DATE,
                    EntityFieldType.DATETIME,
                    EntityFieldType.JSON,
                )

            // Expect
            EntityFieldType.entries
                .filter { !excludes.contains(it) }
                .forEach {
                    assertThat(InOperator.IN.allowed(it))
                        .describedAs("IN should allow ${it.name}")
                        .isTrue()
                }
        }
    }
}
