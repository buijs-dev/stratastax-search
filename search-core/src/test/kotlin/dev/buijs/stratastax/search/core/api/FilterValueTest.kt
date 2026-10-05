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
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FilterValueTest {

    private val string = EntityField("name", EntityFieldType.STRING)
    private val number = EntityField("score", EntityFieldType.NUMBER)
    private val boolean = EntityField("active", EntityFieldType.BOOLEAN)
    private val date = EntityField("day", EntityFieldType.DATE)
    private val dateTime = EntityField("createdAt", EntityFieldType.DATETIME)
    private val uuid = EntityField("id", EntityFieldType.UUID)
    private val json = EntityField("payload", EntityFieldType.JSON)
    private val enum =
        EntityField("status", EntityFieldType.ENUM, enumValues = setOf("ACTIVE", "PENDING"))

    /** The public overload; the two-argument call resolves to the internal parse-result one. */
    private fun parse(
        field: EntityField,
        value: String,
        nullable: Boolean = true,
        quoted: Boolean = false,
    ) = FilterValue.of(field, value, nullable, quoted)

    @Nested
    inner class ValidValues {

        @Test
        fun `string is taken as is`() {
            assertThat(parse(string, "Alain")).isEqualTo(FilterValue.StringValue("Alain"))
        }

        @Test
        fun `number is parsed as BigDecimal`() {
            assertThat(parse(number, "12.50"))
                .isEqualTo(FilterValue.NumberValue(BigDecimal("12.50")))
        }

        @Test
        fun `boolean is case-insensitive`() {
            assertThat(parse(boolean, "TRUE")).isEqualTo(FilterValue.BooleanValue(true))
            assertThat(parse(boolean, "False")).isEqualTo(FilterValue.BooleanValue(false))
        }

        @Test
        fun `date and datetime are parsed as Instant`() {
            val instant = Instant.parse("2026-01-01T10:15:30Z")

            assertThat(parse(date, "2026-01-01T10:15:30Z"))
                .isEqualTo(FilterValue.DateTimeValue(instant))
            assertThat(parse(dateTime, "2026-01-01T10:15:30Z"))
                .isEqualTo(FilterValue.DateTimeValue(instant))
        }

        @Test
        fun `uuid is parsed`() {
            val id = UUID.randomUUID()

            assertThat(parse(uuid, id.toString())).isEqualTo(FilterValue.UuidValue(id))
        }

        @Test
        fun `enum is matched case-sensitively`() {
            assertThat(parse(enum, "ACTIVE")).isEqualTo(FilterValue.EnumValue("ACTIVE"))
            assertThatThrownBy { parse(enum, "active") }.isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `json is kept as raw text`() {
            assertThat(parse(json, "{not even json"))
                .isEqualTo(FilterValue.JsonValue("{not even json"))
        }
    }

    @Nested
    inner class InvalidValues {

        @Test
        fun `invalid boolean`() {
            assertThatThrownBy { parse(boolean, "maybe") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage(
                    "Invalid boolean value 'maybe' for field 'active'. Expected 'true' or 'false'"
                )
        }

        @Test
        fun `invalid date-time`() {
            assertThatThrownBy { parse(dateTime, "yesterday") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Invalid ISO-8601 UTC date-time 'yesterday' for field 'createdAt'")
        }

        @Test
        fun `invalid enum lists the allowed values`() {
            assertThatThrownBy { parse(enum, "GONE") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage(
                    "Invalid enum value 'GONE' for field 'status'. Allowed values: ACTIVE, PENDING"
                )
        }

        @Test
        fun `invalid number`() {
            assertThatThrownBy { parse(number, "abc") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Invalid number format 'abc' for field 'score'")
        }

        @Test
        fun `invalid uuid`() {
            assertThatThrownBy { parse(uuid, "nope") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Invalid UUID format 'nope' for field 'id'")
        }
    }

    @Nested
    inner class NullHandling {

        @Test
        fun `unquoted null is NullValue when nullable`() {
            assertThat(parse(string, "null")).isEqualTo(FilterValue.NullValue)
            assertThat(parse(string, "NULL", nullable = true)).isEqualTo(FilterValue.NullValue)
        }

        @Test
        fun `unquoted null is rejected when not nullable`() {
            assertThatThrownBy { parse(number, "null", nullable = false) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("NULL is only supported with '==' or '!=' for field 'score'")
        }

        @Test
        fun `quoted null is just text`() {
            assertThat(parse(string, "null", nullable = false, quoted = true))
                .isEqualTo(FilterValue.StringValue("null"))
        }
    }

    @Nested
    inner class ParseResults {

        @Test
        fun `a valid value is wrapped in ValidFilterValue`() {
            assertThat(FilterValue.of(number, "1"))
                .isEqualTo(ValidFilterValue(FilterValue.NumberValue(BigDecimal("1"))))
        }

        @Test
        fun `every failure maps to its own CorruptFilterValue`() {
            assertThat(FilterValue.of(number, "x"))
                .isEqualTo(CorruptFilterValue.InvalidNumber(number, "x"))
            assertThat(FilterValue.of(boolean, "x"))
                .isEqualTo(CorruptFilterValue.InvalidBoolean(boolean, "x"))
            assertThat(FilterValue.of(dateTime, "x"))
                .isEqualTo(CorruptFilterValue.InvalidDateTime(dateTime, "x"))
            assertThat(FilterValue.of(date, "x"))
                .isEqualTo(CorruptFilterValue.InvalidDateTime(date, "x"))
            assertThat(FilterValue.of(uuid, "x"))
                .isEqualTo(CorruptFilterValue.InvalidUuid(uuid, "x"))
            assertThat(FilterValue.of(enum, "x"))
                .isEqualTo(CorruptFilterValue.InvalidEnum(enum, "x"))
        }

        @Test
        fun `a corrupt value exposes its field and raw value`() {
            val corrupt = CorruptFilterValue.InvalidUuid(uuid, "x")

            assertThat(corrupt.field).isEqualTo(uuid)
            assertThat(corrupt.value).isEqualTo("x")
        }
    }
}
