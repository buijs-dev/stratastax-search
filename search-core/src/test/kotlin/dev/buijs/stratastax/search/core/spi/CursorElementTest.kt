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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Instant

class CursorElementTest {

    @Nested
    inner class StringValueTest {

        @Test
        fun `should encode and serialize simple string`() {
            val element = CursorElement.StringValue("hello")

            assertThat(element.prefix()).isEqualTo("S")
            assertThat(element.serialize()).isEqualTo("hello")
            assertThat(element.encode()).isEqualTo("S:hello")
        }

        @Test
        fun `should properly escape delimiter and percent signs during serialization`() {
            val element = CursorElement.StringValue("foo|bar%baz")

            assertThat(element.serialize()).isEqualTo("foo%7Cbar%25baz")
            assertThat(element.encode()).isEqualTo("S:foo%7Cbar%25baz")
        }

        @Test
        fun `should restore escaped string from serialized form`() {
            val raw = "foo%7Cbar%25baz"
            val restored = CursorElement.StringValue.of(raw)

            assertThat(restored.value).isEqualTo("foo|bar%baz")
        }

        @Test
        fun `should maintain data integrity through round trip encoding and decoding`() {
            val original =
                CursorElement.StringValue("complex|value%with%7Cdelimiters|and%25percents")
            val encoded = original.encode()
            val decoded = CursorElement.of(encoded)

            assertThat(decoded).isEqualTo(original)
        }
    }

    @Nested
    inner class NumberValueTest {

        @Test
        fun `should encode BigDecimal value using plain string representation`() {
            val element = CursorElement.NumberValue(BigDecimal("123.456000"))

            assertThat(element.prefix()).isEqualTo("N")
            assertThat(element.serialize()).isEqualTo("123.456000")
            assertThat(element.encode()).isEqualTo("N:123.456000")
        }

        @Test
        fun `should create NumberValue from Int helper method`() {
            val element = CursorElement.NumberValue.of(42)

            assertThat(element.value).isEqualTo(BigDecimal(42))
            assertThat(element.encode()).isEqualTo("N:42")
        }

        @Test
        fun `should restore NumberValue from string`() {
            val restored = CursorElement.NumberValue.of("987654321.123456789")

            assertThat(restored.value).isEqualTo(BigDecimal("987654321.123456789"))
        }

        @Test
        fun `should maintain data integrity through round trip encoding and decoding`() {
            val original = CursorElement.NumberValue(BigDecimal("-999999999999999999.99"))
            val encoded = original.encode()
            val decoded = CursorElement.of(encoded)

            assertThat(decoded).isEqualTo(original)
        }
    }

    @Nested
    inner class BooleanValueTest {

        @Test
        fun `should encode boolean values`() {
            val trueElement = CursorElement.BooleanValue(true)
            val falseElement = CursorElement.BooleanValue(false)

            assertThat(trueElement.prefix()).isEqualTo("B")
            assertThat(trueElement.serialize()).isEqualTo("true")
            assertThat(trueElement.encode()).isEqualTo("B:true")

            assertThat(falseElement.encode()).isEqualTo("B:false")
        }

        @Test
        fun `should restore BooleanValue from valid string`() {
            assertThat(CursorElement.BooleanValue.of("true").value).isEqualTo(true)
            assertThat(CursorElement.BooleanValue.of("false").value).isEqualTo(false)
        }

        @ParameterizedTest
        @ValueSource(strings = ["True", "FALSE", "1", "0", "yes", ""])
        fun `should throw exception when restoring BooleanValue from non-strict string`(
            invalidInput: String,
        ) {
            assertThatThrownBy { CursorElement.BooleanValue.of(invalidInput) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should maintain data integrity through round trip encoding and decoding`() {
            val original = CursorElement.BooleanValue(true)
            val encoded = original.encode()
            val decoded = CursorElement.of(encoded)

            assertThat(decoded).isEqualTo(original)
        }
    }

    @Nested
    inner class TimestampValueTest {

        @Test
        fun `should encode Instant value into seconds and nanos representation`() {
            val instant = Instant.ofEpochSecond(1700000000L, 123456789L)
            val element = CursorElement.TimestampValue(instant)

            assertThat(element.prefix()).isEqualTo("T")
            assertThat(element.serialize()).isEqualTo("1700000000,123456789")
            assertThat(element.encode()).isEqualTo("T:1700000000,123456789")
        }

        @Test
        fun `should restore TimestampValue from valid string format`() {
            val restored = CursorElement.TimestampValue.of("1700000000,123456789")

            assertThat(restored.value).isEqualTo(Instant.ofEpochSecond(1700000000L, 123456789L))
        }

        @ParameterizedTest
        @ValueSource(strings = ["1700000000", "1700000000-123456789", "invalid"])
        fun `should throw SearchException when restoring TimestampValue without comma delimiter`(
            invalidInput: String,
        ) {
            assertThatThrownBy { CursorElement.TimestampValue.of(invalidInput) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("Invalid cursor element format")
        }

        @Test
        fun `should maintain data integrity through round trip encoding and decoding`() {
            val original = CursorElement.TimestampValue(Instant.now())
            val encoded = original.encode()
            val decoded = CursorElement.of(encoded)

            assertThat(decoded).isEqualTo(original)
        }
    }

    @Nested
    inner class NullValueTest {

        @Test
        fun `should encode NullValue`() {
            val element = CursorElement.NullValue

            assertThat(element.prefix()).isEqualTo("Z")
            assertThat(element.serialize()).isEqualTo("")
            assertThat(element.encode()).isEqualTo("Z:")
        }

        @Test
        fun `should parse NullValue from encoded string`() {
            val decoded = CursorElement.of("Z:")

            assertThat(decoded).isEqualTo(CursorElement.NullValue)
        }
    }

    @Nested
    inner class ParsingAndValidationTest {

        @Test
        fun `should throw SearchException when type value delimiter is missing`() {
            assertThatThrownBy { CursorElement.of("S_no_delimiter") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Invalid cursor element format: S_no_delimiter")
        }

        @Test
        fun `should throw SearchException when type discriminator is unknown`() {
            assertThatThrownBy { CursorElement.of("X:someValue") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Unknown cursor type discriminator: X")
        }

        @Test
        fun `should verify constant delimiter values`() {
            assertThat(CursorElement.COMPONENT_DELIMITER).isEqualTo("|")
            assertThat(CursorElement.COMPONENT_DELIMITER_CHAR).isEqualTo('|')
            assertThat(CursorElement.TYPE_VALUE_DELIMITER).isEqualTo(":")
        }
    }
}
