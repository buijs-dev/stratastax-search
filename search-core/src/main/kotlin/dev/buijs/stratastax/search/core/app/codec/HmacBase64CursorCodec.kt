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
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

internal class HmacBase64CursorCodec(
    signingKey: CursorSigningKey,
    private val version: String = "1",
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding(),
    private val decoder: Base64.Decoder = Base64.getUrlDecoder(),
    private val clock: Clock = Clock.systemUTC(),
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
            append(cursor.hash)
            append(CursorElement.COMPONENT_DELIMITER)
            append(cursor.direction.code)
            append(CursorElement.COMPONENT_DELIMITER)
            append((cursor.issuedAt ?: clock.instant()).epochSecond)
            append(CursorElement.COMPONENT_DELIMITER)
            append(encodeCriteriaPart(cursor.criteria?.sort))
            append(CursorElement.COMPONENT_DELIMITER)
            append(encodeCriteriaPart(cursor.criteria?.filter))
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
        // payload, so a tampered/forged cursor is rejected uniformly without leaking,
        // via the returned error variant,
        // which part of the payload structure was inspected.
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

        val cursorHash =
            reader.next() //
            ?: return CursorDecodeAttempt.Error.MissingHash

        val direction =
            CursorDirection.ofCode(reader.next()) //
            ?: return CursorDecodeAttempt.Error.BadDirection

        val issuedAt =
            reader.next()?.toLongOrNull()?.let { Instant.ofEpochSecond(it) }
                ?: return CursorDecodeAttempt.Error.BadIssuedAt

        val sortPart =
            reader.next() //
            ?: return CursorDecodeAttempt.Error.BadCriteria

        val filterPart =
            reader.next() //
            ?: return CursorDecodeAttempt.Error.BadCriteria

        val criteria =
            decodeCriteria(sortPart, filterPart).getOrElse {
                return CursorDecodeAttempt.Error.BadCriteria
            }

        val values =
            runCatching { reader.remainingValues() }
                .getOrElse {
                    return CursorDecodeAttempt.Error.BadCursorElement
                }
                .ifEmpty {
                    return CursorDecodeAttempt.Error.IncompleteCursor
                }

        return CursorDecodeAttempt.Success(
            state =
                CursorState(
                    values = values,
                    hash = cursorHash,
                    direction = direction,
                    issuedAt = issuedAt,
                    criteria = criteria,
                )
        )
    }

    private fun encodeCriteriaPart(part: String?): String =
        (part?.let { CursorElement.StringValue(it) } ?: CursorElement.NullValue).encode()

    // A cursor built without criteria carries a null sort; otherwise sort is text, filter text or null.
    private fun decodeCriteria(sortPart: String, filterPart: String): Result<CursorCriteria?> =
        runCatching {
            val sort = CursorElement.of(sortPart)
            val filter = CursorElement.of(filterPart)
            when (sort) {
                is CursorElement.NullValue -> null
                is CursorElement.StringValue if
                    (filter is CursorElement.StringValue || filter is CursorElement.NullValue)
                 -> CursorCriteria(sort.value, (filter as? CursorElement.StringValue)?.value)
                else -> throw IllegalArgumentException("Unexpected criteria elements")
            }
        }

    private fun sign(payload: ByteArray): String {
        val signature = mac.get().doFinal(payload)
        return encoder.encodeToString(signature)
    }
}

private sealed interface CursorDecodeAttempt {
    data class Success(val state: CursorState) : CursorDecodeAttempt

    sealed interface Error : CursorDecodeAttempt {
        data object Blank : Error

        data object NotBase64 : Error

        data object BadFormat : Error

        data object BadSignature : Error

        data object WrongVersion : Error

        data object MissingHash : Error

        data object BadDirection : Error

        data object BadIssuedAt : Error

        data object BadCriteria : Error

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
