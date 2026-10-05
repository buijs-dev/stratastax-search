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

import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest

private val log = KotlinLogging.logger {}

/**
 * Deterministic fingerprint of the sort+filter criteria used to produce a page of results, stored
 * in CursorState so a follow-up request carrying that cursor can be validated against the SAME
 * criteria.
 *
 * Without this, a client could change filter/sort between page requests and silently resume
 * iteration from a position that means something different under the new criteria.
 */
object CriteriaHash {

    fun of(sort: Sort, filter: Filter?): String {
        log.debug { "Creating criteria hash for sort=$sort and filter=$filter" }
        val canonical = buildString {
            append("sort=")
            sort.fields.forEach { field ->
                append(field.field.name).append(':').append(field.direction).append(';')
            }
            append("|filter=")
            filter?.let { append(canonicalize(it.root)) }
        }
        return sha256Hex(canonical)
    }

    private fun canonicalize(expression: FilterExpression): String =
        when (expression) {
            is AndExpression ->
                "AND(" + expression.children.joinToString(",") { canonicalize(it) } + ")"

            is OrExpression ->
                "OR(" + expression.children.joinToString(",") { canonicalize(it) } + ")"

            is ComparisonExpression ->
                "${expression.field.name}${expression.operator}${canonicalizeValue(expression.value)}"

            is InExpression ->
                "${expression.field.name}${expression.operator}[" +
                    expression.values.joinToString(",") { canonicalizeValue(it) } +
                    "]"

            is LikeExpression -> "${expression.field.name}=like=${expression.pattern}"
        }

    private fun canonicalizeValue(value: FilterValue): String =
        when (value) {
            is FilterValue.StringValue -> "S:${value.value}"
            is FilterValue.NumberValue -> "N:${value.value.toPlainString()}"
            is FilterValue.BooleanValue -> "B:${value.value}"
            is FilterValue.DateTimeValue -> "T:${value.value}"
            is FilterValue.EnumValue -> "E:${value.value}"
            is FilterValue.UuidValue -> "U:${value.value}"
            is FilterValue.JsonValue -> "J:${value.value}"
            is FilterValue.NullValue -> "Z"
        }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
