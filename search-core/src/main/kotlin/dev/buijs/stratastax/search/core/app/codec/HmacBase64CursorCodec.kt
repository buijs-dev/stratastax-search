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
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

internal class HmacBase64CursorCodec(
    signingKey: CursorSigningKey,
    private val version: String = "1",
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding(),
    private val decoder: Base64.Decoder = Base64.getUrlDecoder(),
) : CursorCodec {

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        val charset = Charsets.UTF_8
    }

    private val keySpec = SecretKeySpec(signingKey.bytes(), HMAC_ALGORITHM)

    private val mac =
        ThreadLocal.withInitial { Mac.getInstance(HMAC_ALGORITHM).apply { init(keySpec) } }

    override fun encode(cursor: CursorState): String {
        val payload = buildString {
            append(version)
            append(CursorElement.COMPONENT_DELIMITER)
            append(cursor.criteriaHash)
            for (element in cursor.values) {
                append(CursorElement.COMPONENT_DELIMITER)
                append(element.encode())
            }
        }

        val payloadBytes = payload.toByteArray(charset)
        val signature = sign(payloadBytes)
        val signedPayload = buildString {
            append(payload)
            append(CursorElement.COMPONENT_DELIMITER)
            append(signature)
        }

        return encoder.encodeToString(signedPayload.toByteArray(charset))
    }

    /**
     * Decodes [cursor], or returns `null` on any failure. Deliberately collapses every distinct
     * failure reason ([CursorDecodeAttempt.Error]) into the same `null` result at this public
     * boundary - not telling an untrusted caller *why* a cursor was rejected (blank vs. bad base64
     * vs. bad signature vs. ...) avoids handing them a signature-oracle. The specific reason is
     * still logged server-side at debug level below, for our own observability.
     */
    override fun decode(cursor: String): CursorState? =
        when (val result = tryToDecode(cursor)) {
            is CursorDecodeAttempt.Success -> result.state
            is CursorDecodeAttempt.Error -> {
                logger.debug { "Cursor decode failed: $result" }
                null
            }
        }

    private fun tryToDecode(cursor: String): CursorDecodeAttempt {
        if (cursor.isBlank()) {
            return CursorDecodeAttempt.Error.Blank
        }

        val signedPayload =
            runCatching { String(decoder.decode(cursor), charset) }
                .getOrElse {
                    return CursorDecodeAttempt.Error.NotBase64
                }

        val signatureIndex = signedPayload.lastIndexOf(CursorElement.COMPONENT_DELIMITER)

        if (signatureIndex < 0) {
            return CursorDecodeAttempt.Error.BadFormat
        }

        // Verify the signature before any further structural parsing of the
        // payload, so a
        // tampered/forged cursor is rejected uniformly without leaking, via the
        // returned error
        // variant, which part of the payload structure was inspected.
        val payload = signedPayload.substring(0, signatureIndex)
        val payloadSigned = sign(payload.toByteArray(charset))
        val signature = signedPayload.substring(signatureIndex + 1)
        if (!constantTimeEquals(payloadSigned, signature)) {
            return CursorDecodeAttempt.Error.BadSignature
        }

        val reader = CursorReader(payload)
        if (reader.next() != version) {
            return CursorDecodeAttempt.Error.WrongVersion
        }

        val cursorHash = reader.next() ?: return CursorDecodeAttempt.Error.MissingHash

        val values =
            runCatching { reader.remainingValues() }
                .getOrElse {
                    return CursorDecodeAttempt.Error.BadCursorElement
                }
                .ifEmpty {
                    return CursorDecodeAttempt.Error.IncompleteCursor
                }

        return CursorDecodeAttempt.Success(CursorState(values, cursorHash))
    }

    private fun sign(payload: ByteArray): String {
        val signature = mac.get().doFinal(payload)
        return encoder.encodeToString(signature)
    }
}

/**
 * Outcome of a single cursor-decode attempt. Private to this file - it exists purely to make
 * [HmacBase64CursorCodec.tryToDecode] exhaustive and give us a specific reason to log; nothing
 * outside this codec should ever see it, since [CursorCodec.decode] only ever exposes a plain
 * `CursorState?` to callers.
 */
private sealed interface CursorDecodeAttempt {
    data class Success(val state: CursorState) : CursorDecodeAttempt

    sealed interface Error : CursorDecodeAttempt {
        data object Blank : Error

        data object NotBase64 : Error

        data object BadFormat : Error

        data object BadSignature : Error

        data object WrongVersion : Error

        data object MissingHash : Error

        data object BadCursorElement : Error

        data object IncompleteCursor : Error
    }
}

// Avoids short-circuiting string comparison, which could otherwise leak timing
// information about how many leading characters of a forged signature are
// correct.
private fun constantTimeEquals(expected: String, actual: String): Boolean {
    if (expected.length != actual.length) {
        return false
    }

    var result = 0
    for (i in expected.indices) {
        result = result or (expected[i].code xor actual[i].code)
    }

    return result == 0
}

private fun CursorReader.remainingValues(): List<CursorElement> = buildList {
    var value = next()
    while (value != null) {
        add(CursorElement.of(value))
        value = next()
    }
}

private class CursorReader(private val payload: String) {
    private var start = 0

    fun next(): String? {
        if (start > payload.length) {
            return null
        }

        val end = payload.indexOf(CursorElement.COMPONENT_DELIMITER_CHAR, start)
        return if (end == -1) {
            val value = payload.substring(start)
            start = payload.length + 1
            value.ifBlank { null }
        } else {
            val value = payload.substring(start, end)
            start = end + 1
            value.ifBlank { null }
        }
    }
}
