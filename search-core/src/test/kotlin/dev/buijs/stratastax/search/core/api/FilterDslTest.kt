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
import dev.buijs.stratastax.search.testdata.Status
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class FilterDslTest {

    private val stringField = EntityField("name", EntityFieldType.STRING)
    private val numberField = EntityField("score", EntityFieldType.NUMBER)
    private val booleanField = EntityField("active", EntityFieldType.BOOLEAN)
    private val uuidField = EntityField("id", EntityFieldType.UUID)
    private val dateTimeField = EntityField("createdAt", EntityFieldType.DATETIME)
    private val enumField =
        EntityField("status", EntityFieldType.ENUM, enumValues = setOf("ACTIVE", "PENDING"))

    @Test
    fun `eq builds a ComparisonExpression with the typed value, no string round-trip`() {
        val expr = stringField eq "Alain"
        assertThat(expr)
            .isEqualTo(
                ComparisonExpression(
                    stringField,
                    ComparisonOperator.EQUALS,
                    FilterValue.StringValue("Alain"),
                ),
            )
    }

    @Test
    fun `eq accepts null`() {
        val expr = stringField eq null
        assertThat(expr.value).isEqualTo(FilterValue.NullValue)
    }

    @Test
    fun `ne builds NOT_EQUALS`() {
        val expr = numberField ne 5
        assertThat(expr.operator).isEqualTo(ComparisonOperator.NOT_EQUALS)
        assertThat(expr.value).isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(5)))
    }

    // == typed values per EntityFieldType ==

    @Test
    fun `eq wraps Int Long Double BigDecimal as NumberValue`() {
        assertThat((numberField eq 5).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(5)))
        assertThat((numberField eq 5L).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(5L)))
        assertThat((numberField eq 5.5).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(5.5)))
        assertThat((numberField eq BigDecimal.TEN).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.TEN))
    }

    @Test
    fun `eq wraps Boolean as BooleanValue`() {
        assertThat((booleanField eq true).value).isEqualTo(FilterValue.BooleanValue(true))
    }

    @Test
    fun `eq wraps UUID as UuidValue`() {
        val id = UUID.randomUUID()
        assertThat((uuidField eq id).value).isEqualTo(FilterValue.UuidValue(id))
    }

    @Test
    fun `eq wraps Instant and OffsetDateTime as DateTimeValue`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        assertThat((dateTimeField eq instant).value).isEqualTo(FilterValue.DateTimeValue(instant))
        assertThat((dateTimeField eq instant.atOffset(ZoneOffset.UTC)).value)
            .isEqualTo(FilterValue.DateTimeValue(instant))
    }

    @Test
    fun `eq wraps a valid enum name (String) or Enum constant as EnumValue`() {
        assertThat((enumField eq "ACTIVE").value).isEqualTo(FilterValue.EnumValue("ACTIVE"))
        assertThat((enumField eq Status.ACTIVE).value).isEqualTo(FilterValue.EnumValue("ACTIVE"))
    }

    @Test
    fun `eq rejects an enum value not in enumValues`() {
        assertThatThrownBy { enumField eq "UNKNOWN" }.isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `eq throws a clear SearchException on a type mismatch`() {
        assertThatThrownBy { stringField eq 123 }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'name' is STRING, but got a value of type Int")
    }

    @Test
    fun `eq wraps Float and Short as NumberValue`() {
        assertThat((numberField eq 1.5f).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(1.5)))
        assertThat((numberField eq 7.toShort()).value)
            .isEqualTo(FilterValue.NumberValue(BigDecimal.valueOf(7)))
    }

    @Test
    fun `eq rejects a non-numeric value for a NUMBER field`() {
        assertThatThrownBy { numberField eq "5" }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'score' is NUMBER, but got a value of type String")
    }

    @Test
    fun `eq rejects a non-temporal value for a DATETIME field`() {
        assertThatThrownBy { dateTimeField eq "2026-01-01T00:00:00Z" }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Field 'createdAt' is DATETIME, but got a value of type String")
    }

    // == gt/ge/lt/le - restricted to NUMBER/DATE/DATETIME ==

    @Test
    fun `gt ge lt le work on a NUMBER field`() {
        assertThat((numberField gt 1).operator).isEqualTo(ComparisonOperator.GREATER_THAN)
        assertThat((numberField ge 1).operator).isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL)
        assertThat((numberField lt 1).operator).isEqualTo(ComparisonOperator.LESS_THAN)
        assertThat((numberField le 1).operator).isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL)
    }

    @Test
    fun `gt is rejected for a STRING field, same as the existing string-based path`() {
        assertThatThrownBy { stringField gt "a" }.isInstanceOf(SearchException::class.java)
    }

    // Not a @Test - Kotlin refuses to compile this at all, which is the point:
    // val invalid = numberField gt null

    // == isIn/notIn ==

    @Test
    fun `isIn builds an InExpression with typed values`() {
        val expr = numberField isIn listOf(1, 2, 3)
        assertThat(expr.operator).isEqualTo(InOperator.IN)
        assertThat(expr.values)
            .isEqualTo(
                listOf(
                    FilterValue.NumberValue(BigDecimal.valueOf(1)),
                    FilterValue.NumberValue(BigDecimal.valueOf(2)),
                    FilterValue.NumberValue(BigDecimal.valueOf(3)),
                ),
            )
    }

    @Test
    fun `notIn builds OUT`() {
        val expr = stringField notIn listOf("a", "b")
        assertThat(expr.operator).isEqualTo(InOperator.OUT)
    }

    @Test
    fun `isIn requires a non-empty collection`() {
        assertThatThrownBy { stringField isIn emptyList() }
            .isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `isIn is rejected for a BOOLEAN field, same as InOperator allowed()`() {
        assertThatThrownBy { booleanField isIn listOf(true) }
            .isInstanceOf(SearchException::class.java)
    }

    // == ilike/containsIgnoreCase ==

    @Test
    fun `ilike builds a case-insensitive LikeExpression`() {
        assertThat(stringField.ilike("a%"))
            .isEqualTo(LikeExpression(stringField, "a%", ignoreCase = true))
    }

    @Test
    fun `containsIgnoreCase wraps the value in wildcards`() {
        assertThat(stringField.containsIgnoreCase("Al"))
            .isEqualTo(LikeExpression(stringField, "%Al%", ignoreCase = true))
    }

    @Test
    fun `containsIgnoreCase escapes wildcards and backslashes in the value`() {
        assertThat(stringField.containsIgnoreCase("""50%_a\b""").pattern)
            .isEqualTo("""%50\%\_a\\b%""")
    }

    @Test
    fun `containsIgnoreCase rejects a non-string field`() {
        assertThatThrownBy { numberField.containsIgnoreCase("1") }
            .isInstanceOf(SearchException::class.java)
    }
}
