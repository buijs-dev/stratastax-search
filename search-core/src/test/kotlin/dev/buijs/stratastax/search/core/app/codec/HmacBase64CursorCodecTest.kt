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
package dev.buijs.stratastax.search.core.app.codec

import dev.buijs.stratastax.search.core.api.CursorState
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorElement.Companion.TYPE_VALUE_DELIMITER
import dev.buijs.stratastax.search.core.spi.CursorElement.NumberValue
import dev.buijs.stratastax.search.core.spi.CursorElement.StringValue
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HmacBase64CursorCodecTest {

    private val signingKey = CursorSigningKey { "super-secret-key".toByteArray(Charsets.UTF_8) }

    private val subject = HmacBase64CursorCodec(signingKey)
    private val state = CursorState(listOf(NumberValue.of(123), StringValue("foo")), "some-hash")
    private val encoded = subject.encode(state)

    @Test
    fun `encode and decode should be symmetrical`() {
        // When
        val decoded = subject.decode(encoded)

        // Then
        assertThat(decoded).isEqualTo(state)
    }

    @Test
    fun `decode should fail when signature is incorrect`() {
        // Given
        val anotherSigningKey = CursorSigningKey {
            "even-more-secret-key".toByteArray(Charsets.UTF_8)
        }

        val anotherCodec = HmacBase64CursorCodec(anotherSigningKey, "1")

        // When
        val result = anotherCodec.decode(encoded)

        // Then
        // TODO assertInstanceOf(BadSignature::class.java, result)
    }

    @Test
    fun `decode should fail for wrong version`() {
        // Given
        val anotherCodec = HmacBase64CursorCodec(signingKey, "99")

        // When
        val result = anotherCodec.decode(encoded)

        // Then
        // TODO assertInstanceOf(WrongVersion::class.java, result)
    }

    @Test
    fun `decode should fail for malformed input`() {
        // When
        val result = subject.decode("not-base64-at-all!")

        // Then
        // TODO assertInstanceOf(NotBase64::class.java, result)
    }

    @Test
    fun `decode should fail for blank cursor`() {
        // When
        val result = subject.decode(" ")

        // Then
        // TODO assertInstanceOf(Blank::class.java, result)
    }

    @Test
    fun `decode should fail for a badly formatted cursor`() {
        // When
        val result = subject.decode("MQ==")

        // Then
        // TODO assertInstanceOf(BadFormat::class.java, result)
    }

    @Test
    fun `decode should fail for a missing hash`() {
        // Given
        val state = CursorState(emptyList(), "")

        // When
        val encoded = subject.encode(state)
        val result = subject.decode(encoded)

        // Then
        // TODO assertInstanceOf(MissingHash::class.java, result)
    }

    @Test
    fun `decode should fail for an empty values list`() {
        // Given
        val state = CursorState(emptyList(), "hashicorp")

        // When
        val encoded = subject.encode(state)
        val result = subject.decode(encoded)

        // Then
        // TODO assertInstanceOf(IncompleteCursor::class.java, result)
    }

    @Test
    fun `decode should fail when one or more elements are invalid`() {
        // Given
        val value = mockk<CursorElement>()
        every { value.encode() } returns "X${TYPE_VALUE_DELIMITER}foo"
        val state = CursorState(listOf(value), "hash")

        // When - Unknown discriminator X ("1|hash|X:foo")
        val encoded = subject.encode(state)
        val result = subject.decode(encoded)

        // Then
        // TODO assertInstanceOf(BadCursorElement::class.java, result)
    }
}
