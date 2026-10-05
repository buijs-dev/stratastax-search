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

import dev.buijs.stratastax.search.core.api.CursorCriteria
import dev.buijs.stratastax.search.core.api.CursorDirection
import dev.buijs.stratastax.search.core.api.CursorState
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorElement.Companion.TYPE_VALUE_DELIMITER
import dev.buijs.stratastax.search.core.spi.CursorElement.NumberValue
import dev.buijs.stratastax.search.core.spi.CursorElement.StringValue
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HmacBase64CursorCodecTest {

    private val signingKey = CursorSigningKey { "super-secret-key".toByteArray(Charsets.UTF_8) }

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val subject =
        HmacBase64CursorCodec(signingKey, clock = Clock.fixed(now, ZoneOffset.UTC))
    private val state = CursorState(listOf(NumberValue.of(123), StringValue("foo")), "some-hash")
    private val encoded = subject.encode(state)

    @Test
    fun `encode and decode should be symmetrical`() {
        // When
        val decoded = subject.decode(encoded)

        // Then
        assertThat(decoded).isEqualTo(state.copy(issuedAt = now))
    }

    @Test
    fun `criteria survive encoding, including delimiter and escape characters`() {
        val criteria = CursorCriteria("name:ASC", "name==\"a|b%c;d\"")
        val withCriteria = state.copy(criteria = criteria)

        val decoded = subject.decode(subject.encode(withCriteria))

        assertThat(decoded!!.criteria).isEqualTo(criteria)
        assertThat(decoded.values).isEqualTo(state.values)
    }

    @Test
    fun `criteria without a filter survive encoding`() {
        val criteria = CursorCriteria("name:ASC", null)

        assertThat(subject.decode(subject.encode(state.copy(criteria = criteria)))!!.criteria)
            .isEqualTo(criteria)
    }

    @Test
    fun `a cursor without criteria decodes without criteria`() {
        assertThat(subject.decode(encoded)!!.criteria).isNull()
    }

    @Test
    fun `a validly signed cursor with malformed criteria is rejected`() {
        val value = NumberValue.of(123).encode()

        assertThat(subject.decode(signed("1|h|A|1767225600|N:1|Z:|$value"))).isNull()
        assertThat(subject.decode(signed("1|h|A|1767225600|S:name:ASC|N:1|$value"))).isNull()
        assertThat(subject.decode(signed("1|h|A|1767225600|S:name:ASC"))).isNull()
    }

    @Test
    fun `encode stamps the cursor with the current time`() {
        assertThat(subject.decode(encoded)!!.issuedAt).isEqualTo(now)
    }

    @Test
    fun `encode keeps the original issue time of an already issued cursor`() {
        val earlier = now.minusSeconds(3600)

        val decoded = subject.decode(subject.encode(state.copy(issuedAt = earlier)))

        assertThat(decoded!!.issuedAt).isEqualTo(earlier)
    }

    @Test
    fun `a validly signed cursor without a valid issue time is rejected`() {
        val payload = "1|some-hash|A|not-a-number|Z:|Z:|${NumberValue.of(123).encode()}"

        assertThat(subject.decode(signed(payload))).isNull()
    }

    @Test
    fun `the direction of a cursor survives encoding`() {
        val before = state.copy(direction = CursorDirection.BEFORE)

        assertThat(subject.decode(subject.encode(before))).isEqualTo(before.copy(issuedAt = now))
        assertThat(subject.decode(encoded)!!.direction).isEqualTo(CursorDirection.AFTER)
    }

    @Test
    fun `a validly signed cursor from before directions existed is rejected, not misread`() {
        // The old payload: version|hash|elements, where the direction now follows the hash.
        val payload = "1|some-hash|${NumberValue.of(123).encode()}"

        assertThat(subject.decode(signed(payload))).isNull()
    }

    private fun signed(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("super-secret-key".toByteArray(), "HmacSHA256"))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val signature = encoder.encodeToString(mac.doFinal(payload.toByteArray()))
        return encoder.encodeToString("$payload|$signature".toByteArray())
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
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for wrong version`() {
        // Given
        val anotherCodec = HmacBase64CursorCodec(signingKey, "99")

        // When
        val result = anotherCodec.decode(encoded)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for malformed input`() {
        // When
        val result = subject.decode("not-base64-at-all!")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for blank cursor`() {
        // When
        val result = subject.decode(" ")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for a badly formatted cursor`() {
        // When
        val result = subject.decode("MQ==")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for a missing hash`() {
        // Given
        val state = CursorState(emptyList(), "")

        // When
        val encoded = subject.encode(state)
        val result = subject.decode(encoded)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `decode should fail for an empty values list`() {
        // Given
        val state = CursorState(emptyList(), "hashicorp")

        // When
        val encoded = subject.encode(state)
        val result = subject.decode(encoded)

        // Then
        assertThat(result).isNull()
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
        assertThat(result).isNull()
    }

    @Test
    fun `a signature of the wrong length is rejected`() {
        val tampered = rewrite(encoded) { it.dropLast(1) }

        assertThat(subject.decode(tampered)).isNull()
    }

    @Test
    fun `a signature of the right length but with other content is rejected`() {
        val tampered = rewrite(encoded) { it.dropLast(1) + if (it.last() == 'A') 'B' else 'A' }

        assertThat(subject.decode(tampered)).isNull()
    }

    @Test
    fun `a validly signed cursor without criteria parts is rejected`() {
        assertThat(subject.decode(signed("1|h|A|1767225600"))).isNull()
        assertThat(subject.decode(signed("1|h|A|1767225600|Z:"))).isNull()
    }

    @Test
    fun `a validly signed cursor with an unknown direction is rejected`() {
        val value = NumberValue.of(123).encode()

        assertThat(subject.decode(signed("1|h|X|1767225600|Z:|Z:|$value"))).isNull()
    }

    @Test
    fun `a validly signed cursor with a malformed element is rejected`() {
        assertThat(subject.decode(signed("1|h|A|1767225600|Z:|Z:|nonsense"))).isNull()
    }

    @Test
    fun `a validly signed cursor is accepted`() {
        val value = NumberValue.of(123).encode()

        assertThat(subject.decode(signed("1|h|B|1767225600|S:name:ASC|Z:|$value")))
            .isEqualTo(
                CursorState(
                    listOf(NumberValue.of(123)),
                    "h",
                    CursorDirection.BEFORE,
                    Instant.ofEpochSecond(1767225600),
                    CursorCriteria("name:ASC", null),
                )
            )
    }

    /** Applies [change] to the signature (the last component) of an encoded cursor. */
    private fun rewrite(cursor: String, change: (String) -> String): String {
        val decoded = String(Base64.getUrlDecoder().decode(cursor))
        val index = decoded.lastIndexOf('|')
        val tampered = decoded.substring(0, index + 1) + change(decoded.substring(index + 1))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.toByteArray())
    }
}
