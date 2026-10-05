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
import java.math.BigDecimal
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The string-based half of the filter DSL, plus the type-mismatch paths of the typed half. */
internal class FilterStringDslTest {

    private val name = EntityField("name", EntityFieldType.STRING)
    private val score = EntityField("score", EntityFieldType.NUMBER)
    private val active = EntityField("active", EntityFieldType.BOOLEAN)
    private val createdAt = EntityField("createdAt", EntityFieldType.DATETIME)
    private val payload = EntityField("payload", EntityFieldType.JSON)
    private val status =
        EntityField("status", EntityFieldType.ENUM, enumValues = setOf("ACTIVE", "PENDING"))

    @Test
    fun `equalsValue and notEqualsValue parse the value for the field type`() {
        assertThat(score.equalsValue("5"))
            .isEqualTo(
                ComparisonExpression(
                    score,
                    ComparisonOperator.EQUALS,
                    FilterValue.NumberValue(BigDecimal("5")),
                )
            )
        assertThat(name.notEqualsValue("x"))
            .isEqualTo(
                ComparisonExpression(
                    name,
                    ComparisonOperator.NOT_EQUALS,
                    FilterValue.StringValue("x"),
                )
            )
    }

    @Test
    fun `equalsValue accepts the null keyword`() {
        assertThat(name.equalsValue("null").value).isEqualTo(FilterValue.NullValue)
    }

    @Test
    fun `equalsValue rejects a value that does not fit the field type`() {
        assertThatThrownBy { score.equalsValue("abc") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Invalid number format 'abc' for field 'score'")
    }

    @Test
    fun `isNull and isNotNull build comparisons against NullValue`() {
        assertThat(name.isNull())
            .isEqualTo(ComparisonExpression(name, ComparisonOperator.EQUALS, FilterValue.NullValue))
        assertThat(name.isNotNull())
            .isEqualTo(
                ComparisonExpression(name, ComparisonOperator.NOT_EQUALS, FilterValue.NullValue)
            )
    }

    @Test
    fun `range operators build their comparison`() {
        assertThat(score.greaterThan("1").operator).isEqualTo(ComparisonOperator.GREATER_THAN)
        assertThat(score.greaterThanOrEqual("1").operator)
            .isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL)
        assertThat(score.lessThan("1").operator).isEqualTo(ComparisonOperator.LESS_THAN)
        assertThat(score.lessThanOrEqual("1").operator)
            .isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL)
        assertThat(createdAt.greaterThan("2026-01-01T00:00:00Z").value)
            .isEqualTo(FilterValue.DateTimeValue(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `range operators are rejected for a field type that cannot be ordered`() {
        assertThatThrownBy { name.greaterThan("a") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Operator 'GREATER_THAN' is not allowed for field 'name' of type STRING")
    }

    @Test
    fun `range operators reject the null keyword`() {
        assertThatThrownBy { score.lessThan("null") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("NULL is only supported with '==' or '!=' for field 'score'")
    }

    @Test
    fun `like builds a LikeExpression for a string field`() {
        assertThat(name.like("*foo*")).isEqualTo(LikeExpression(name, "*foo*"))
    }

    @Test
    fun `like is rejected for a field that is not a string`() {
        assertThatThrownBy { score.like("1*") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("LIKE is not allowed for field 'score' of type NUMBER")
    }

    @Test
    fun `inValues and notInValues parse every value`() {
        assertThat(status.inValues("ACTIVE", "PENDING"))
            .isEqualTo(
                InExpression(
                    status,
                    InOperator.IN,
                    listOf(FilterValue.EnumValue("ACTIVE"), FilterValue.EnumValue("PENDING")),
                )
            )
        assertThat(score.notInValues("1").operator).isEqualTo(InOperator.OUT)
    }

    @Test
    fun `inValues requires at least one value`() {
        assertThatThrownBy { name.inValues() }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("IN on field 'name' requires at least one value")
    }

    @Test
    fun `inValues is rejected for a boolean field`() {
        assertThatThrownBy { active.inValues("true") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Operator 'IN' is not allowed for field 'active' of type BOOLEAN")
    }

    @Test
    fun `inValues rejects the null keyword`() {
        assertThatThrownBy { name.inValues("a", "null") }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("NULL is only supported with '==' or '!=' for field 'name'")
    }

    // == typed DSL: paths not covered by FilterDslTest ==

    @Test
    fun `eq wraps Short and Float as NumberValue`() {
        assertThat((score eq 3.toShort()).value).isEqualTo(FilterValue.NumberValue(BigDecimal(3)))
        assertThat(((score eq 1.5f).value as FilterValue.NumberValue).value)
            .isEqualByComparingTo(BigDecimal("1.5"))
    }

    @Test
    fun `eq wraps a JSON string as JsonValue`() {
        assertThat((payload eq "{\"a\":1}").value).isEqualTo(FilterValue.JsonValue("{\"a\":1}"))
    }

    @Test
    fun `eq rejects a value of the wrong type for every field type`() {
        assertThatThrownBy { score eq "5" }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'score' is NUMBER, but got a value of type String")
        assertThatThrownBy { createdAt eq "2026-01-01T00:00:00Z" }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'createdAt' is DATETIME, but got a value of type String")
        assertThatThrownBy { payload eq 1 }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'payload' is JSON, but got a value of type Int")
        assertThatThrownBy { active eq "true" }.isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `eq rejects an enum value that is neither a String nor an Enum`() {
        assertThatThrownBy { status eq 5 }
            .isInstanceOf(SearchException::class.java)
            .hasMessage(
                "Invalid enum value '5' for field 'status'. Allowed values: ACTIVE, PENDING"
            )
    }
}
