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

import dev.buijs.stratastax.entity.core.api.EntityFieldType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class FilterOperatorTest {

    private val dateTimeOrNumber =
        setOf(EntityFieldType.NUMBER, EntityFieldType.DATE, EntityFieldType.DATETIME)

    private val noSetMembership =
        setOf(
            EntityFieldType.BOOLEAN,
            EntityFieldType.DATE,
            EntityFieldType.DATETIME,
            EntityFieldType.JSON,
        )

    @Test
    fun `values lists every token, longest first`() {
        val expected =
            ComparisonOperator.entries.map { it.token } + InOperator.entries.map { it.token }

        assertThat(FilterOperator.values).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(FilterOperator.values.map { it.length })
            .isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    fun `asMap and of resolve every token`() {
        assertThat(FilterOperator.asMap)
            .hasSize(ComparisonOperator.entries.size + InOperator.entries.size)

        ComparisonOperator.entries.forEach { assertThat(FilterOperator.of(it.token)).isSameAs(it) }
        InOperator.entries.forEach { assertThat(FilterOperator.of(it.token)).isSameAs(it) }
    }

    @Test
    fun `of returns null for an unknown token`() {
        assertThat(FilterOperator.of("=nope=")).isNull()
        assertThat(FilterOperator.of("")).isNull()
    }

    @Test
    fun `nullable is only allowed for equals and not equals`() {
        assertThat(ComparisonOperator.entries.filter { it.nullable })
            .containsExactlyInAnyOrder(ComparisonOperator.EQUALS, ComparisonOperator.NOT_EQUALS)
        assertThat(InOperator.entries.filter { it.nullable }).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(EntityFieldType::class)
    fun `comparison operators are restricted by field type`(type: EntityFieldType) {
        listOf(
                ComparisonOperator.GREATER_THAN,
                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                ComparisonOperator.LESS_THAN,
                ComparisonOperator.LESS_THAN_OR_EQUAL,
            )
            .forEach { assertThat(it.allowed(type)).isEqualTo(type in dateTimeOrNumber) }

        assertThat(ComparisonOperator.LIKE.allowed(type)).isEqualTo(type == EntityFieldType.STRING)
        assertThat(ComparisonOperator.EQUALS.allowed(type)).isTrue()
        assertThat(ComparisonOperator.NOT_EQUALS.allowed(type)).isTrue()
    }

    @ParameterizedTest
    @EnumSource(EntityFieldType::class)
    fun `set operators are not allowed for boolean, temporal and json fields`(
        type: EntityFieldType
    ) {
        InOperator.entries.forEach {
            assertThat(it.allowed(type)).isEqualTo(type !in noSetMembership)
        }
    }
}
