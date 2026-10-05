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
import java.math.BigDecimal
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [CursorElement.ListValue] uses a length-prefixed ("netstring") encoding for its items
 * specifically so it doesn't need the same escaping discipline [CursorElement.StringValue] uses to
 * stay safe inside the top-level, `COMPONENT_DELIMITER`-joined cursor payload - these tests exist
 * to prove that holds even when an item's own serialized form contains every delimiter character in
 * play (`|`, `%`, `:`).
 */
internal class CursorElementListValueTest {

    @Test
    fun `encode then of round-trips a list of mixed types`() {
        val original =
            CursorElement.ListValue(
                listOf(
                    CursorElement.StringValue("a"),
                    CursorElement.NumberValue(BigDecimal.TEN),
                    CursorElement.BooleanValue(true),
                    CursorElement.TimestampValue(Instant.parse("2026-01-01T00:00:00Z")),
                    CursorElement.NullValue,
                )
            )

        assertThat(CursorElement.of(original.encode())).isEqualTo(original)
    }

    @Test
    fun `an empty list round-trips`() {
        val original = CursorElement.ListValue(emptyList())
        assertThat(CursorElement.of(original.encode())).isEqualTo(original)
    }

    @Test
    fun `items containing every reserved delimiter character round-trip untouched`() {
        // "|" is COMPONENT_DELIMITER, "%" is StringValue's own escape marker,
        // ":" is TYPE_VALUE_DELIMITER / ListValue's own length delimiter -
        // none of them should corrupt decoding.
        val original =
            CursorElement.ListValue(
                listOf(
                    CursorElement.StringValue("a|b%c:d"),
                    CursorElement.StringValue("100:not-a-length"),
                )
            )

        assertThat(CursorElement.of(original.encode())).isEqualTo(original)
    }

    @Test
    fun `a nested list round-trips`() {
        val original =
            CursorElement.ListValue(
                listOf(
                    CursorElement.ListValue(listOf(CursorElement.StringValue("inner"))),
                    CursorElement.StringValue("outer"),
                )
            )

        assertThat(CursorElement.of(original.encode())).isEqualTo(original)
    }

    @Test
    fun `a malformed length prefix throws`() {
        assertThatThrownBy { CursorElement.ListValue.of("notanumber:x") }
            .isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `a truncated item throws`() {
        // Declares a 50-char item but only supplies a few.
        assertThatThrownBy { CursorElement.ListValue.of("50:S:short") }
            .isInstanceOf(SearchException::class.java)
    }
}
