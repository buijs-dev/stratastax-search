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
package dev.buijs.stratastax.search.core.spi

import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.testdata.Status
import dev.buijs.stratastax.search.testdata.WrappedUuid
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class ToCursorElementTest {

    @Test
    fun `null converts to NullValue`() {
        assertThat(null.toCursorElement("x")).isEqualTo(CursorElement.NullValue)
    }

    @Test
    fun `a String converts to StringValue`() {
        assertThat("hello".toCursorElement("x")).isEqualTo(CursorElement.StringValue("hello"))
    }

    @Test
    fun `a UUID converts to StringValue via toString`() {
        val id = UUID.randomUUID()
        assertThat(id.toCursorElement("x")).isEqualTo(CursorElement.StringValue(id.toString()))
    }

    @Test
    fun `an Enum converts to StringValue via its name`() {
        assertThat(Status.ACTIVE.toCursorElement("x"))
            .isEqualTo(CursorElement.StringValue("ACTIVE"))
    }

    @Test
    fun `a Boolean converts to BooleanValue`() {
        assertThat(true.toCursorElement("x")).isEqualTo(CursorElement.BooleanValue(true))
    }

    @Test
    fun `numeric types all convert to NumberValue`() {
        assertThat(BigDecimal.TEN.toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.TEN))
        assertThat(1.toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(1)))
        assertThat(2L.toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(2L)))
        assertThat(3.5.toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(3.5)))
        assertThat(4.5f.toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(4.5)))
        assertThat(6.toShort().toCursorElement("x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(6)))
    }

    @Test
    fun `temporal types all convert to TimestampValue`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        assertThat(instant.toCursorElement("x")).isEqualTo(CursorElement.TimestampValue(instant))

        val offsetDateTime = instant.atOffset(ZoneOffset.UTC)
        assertThat((offsetDateTime as OffsetDateTime).toCursorElement("x"))
            .isEqualTo(CursorElement.TimestampValue(instant))

        val localDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
        assertThat(localDateTime.toCursorElement("x"))
            .isEqualTo(CursorElement.TimestampValue(localDateTime.toInstant(ZoneOffset.UTC)))

        val localDate = LocalDate.of(2026, 1, 1)
        assertThat(localDate.toCursorElement("x"))
            .isEqualTo(
                CursorElement.TimestampValue(localDate.atStartOfDay(ZoneOffset.UTC).toInstant()),
            )

        val timestamp = Timestamp.from(instant)
        assertThat(timestamp.toCursorElement("x"))
            .isEqualTo(CursorElement.TimestampValue(timestamp.toInstant()))
    }

    @Test
    fun `an unsupported type throws with a helpful message`() {
        assertThatThrownBy { WrappedUuid(UUID.randomUUID()).toCursorElement("someField") }
            .isInstanceOf(SearchException::class.java)
            .hasMessageContaining("field(\"someField\")")
            .hasMessageContaining("WrappedUuid")
    }

    @Test
    fun `a List converts to a ListValue of the converted items`() {
        assertThat(listOf("a", "b").toCursorElement("x"))
            .isEqualTo(
                CursorElement.ListValue(
                    listOf(CursorElement.StringValue("a"), CursorElement.StringValue("b")),
                ),
            )
    }

    @Test
    fun `a Set converts to a ListValue of the converted items`() {
        assertThat(setOf(1).toCursorElement("x"))
            .isEqualTo(
                CursorElement.ListValue(listOf(CursorElement.NumberValue(BigDecimal.valueOf(1)))),
            )
    }

    @Test
    fun `an empty collection converts to an empty ListValue`() {
        assertThat(emptyList<String>().toCursorElement("x"))
            .isEqualTo(CursorElement.ListValue(emptyList()))
    }

    @Test
    fun `a nested list converts to a nested ListValue`() {
        assertThat(listOf(listOf("a"), emptyList()).toCursorElement("x"))
            .isEqualTo(
                CursorElement.ListValue(
                    listOf(
                        CursorElement.ListValue(listOf(CursorElement.StringValue("a"))),
                        CursorElement.ListValue(emptyList()),
                    ),
                ),
            )
    }

    @Test
    fun `a custom registry can add support for an otherwise-unsupported type`() {
        val id = UUID.randomUUID()
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(
                    object : CursorValueFactory<WrappedUuid> {

                        override val type = WrappedUuid::class

                        override fun convert(
                            value: WrappedUuid,
                            fieldName: String,
                        ): CursorElement = CursorElement.StringValue(value.value.toString())
                    },
                ).build()

        assertThat(WrappedUuid(id).toCursorElement("x", registry))
            .isEqualTo(CursorElement.StringValue(id.toString()))
    }

    @Test
    fun `a custom registry can override a standard type's conversion`() {
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(
                    object : CursorValueFactory<String> {

                        override val type = String::class

                        override fun convert(
                            value: String,
                            fieldName: String,
                        ): CursorElement = CursorElement.StringValue(value.uppercase())
                    },
                ).build()

        assertThat("hello".toCursorElement("x", registry))
            .isEqualTo(CursorElement.StringValue("HELLO"))
    }
}
