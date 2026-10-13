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

import dev.buijs.stratastax.entity.core.api.Sort
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest

private val log = KotlinLogging.logger {}

/**
 * Deterministic fingerprint of the sort and filter that produced a page, stored in [CursorState] so
 * a follow-up request can be validated against the same criteria. Without it, a client could change
 * the criteria between pages and silently resume from a position that means something else.
 */
object CriteriaHash {

    /** Returns the hex SHA-256 fingerprint of [sort] and [filter]. */
    fun of(
        sort: Sort,
        filter: Filter?,
    ): String {
        log.debug { "Creating criteria hash for sort=$sort and filter=$filter" }

        val canonical =
            buildString {
                append("sort=")
                sort.fields
                    .map { "${lengthPrefixed(it.field.name)}:${it.direction};" }
                    .forEach(this::append)
                append("|filter=")
                filter?.let { append(it.root.canonicalize()) }
            }

        return sha256Hex(canonical)
    }

    private fun FilterExpression.canonicalize(): String =
        when (this) {
            is AndExpression -> {
                "AND(" + children.joinToString(",") { it.canonicalize() } + ")"
            }

            is OrExpression -> {
                "OR(" + children.joinToString(",") { it.canonicalize() } + ")"
            }

            is ComparisonExpression -> {
                "${lengthPrefixed(field.name)}${operator}${value.canonicalizeValue()}"
            }

            is InExpression -> {
                lengthPrefixed(field.name) +
                    "$operator[${values.joinToString(",") { it.canonicalizeValue() }}]"
            }

            is LikeExpression -> {
                "${lengthPrefixed(field.name)}${operatorToken}${lengthPrefixed(pattern)}"
            }

        }

    private fun FilterValue.canonicalizeValue(): String =
        when (this) {
            is FilterValue.StringValue -> "S:${lengthPrefixed(value)}"
            is FilterValue.NumberValue -> "N:${value.toPlainString()}"
            is FilterValue.BooleanValue -> "B:$value"
            is FilterValue.DateTimeValue -> "T:$value"
            is FilterValue.EnumValue -> "E:${lengthPrefixed(value)}"
            is FilterValue.UuidValue -> "U:$value"
            is FilterValue.JsonValue -> "J:${lengthPrefixed(value)}"
            is FilterValue.NullValue -> "Z"
        }

    // Free text (field names, strings, patterns) is length-prefixed so that no value can imitate
    // the separators of the canonical form: `in(x,y)` and `in("x,S:y")` must not hash alike.
    private fun lengthPrefixed(text: String) = "${text.length}:$text"

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
