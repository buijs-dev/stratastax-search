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
package dev.buijs.stratastax.search.core.app.filter

import dev.buijs.stratastax.search.core.api.AndExpression
import dev.buijs.stratastax.search.core.api.ComparisonExpression
import dev.buijs.stratastax.search.core.api.Filter
import dev.buijs.stratastax.search.core.api.FilterExpression
import dev.buijs.stratastax.search.core.api.FilterValue
import dev.buijs.stratastax.search.core.api.InExpression
import dev.buijs.stratastax.search.core.api.LikeExpression
import dev.buijs.stratastax.search.core.api.OrExpression

/**
 * Writes a [Filter] as an RSQL expression the [RsqlFilterParser] reads back into an identical tree.
 *
 * Every value is quoted (so it can never be mistaken for a keyword or connector), except the null
 * keyword, which the parser only honors unquoted. Compound children are parenthesized so the
 * original nesting survives the round trip.
 */
internal object FilterWriter {

    fun write(filter: Filter): String = write(filter.root, nested = false)

    private fun write(expression: FilterExpression, nested: Boolean): String =
        when (expression) {
            is AndExpression -> group(expression.children, ";", nested)
            is OrExpression -> group(expression.children, ",", nested)
            is ComparisonExpression ->
                "${expression.field.name}${expression.operator.token}${value(expression.value)}"
            is InExpression ->
                "${expression.field.name}${expression.operator.token}(" +
                    expression.values.joinToString(",") { value(it) } +
                    ")"
            is LikeExpression -> "${expression.field.name}=like=${quote(expression.pattern)}"
        }

    private fun group(children: List<FilterExpression>, separator: String, nested: Boolean) =
        children
            .joinToString(separator) { write(it, nested = true) }
            .let { if (nested) "($it)" else it }

    private fun value(value: FilterValue): String =
        when (value) {
            is FilterValue.StringValue -> quote(value.value)
            is FilterValue.NumberValue -> quote(value.value.toPlainString())
            is FilterValue.BooleanValue -> quote(value.value.toString())
            is FilterValue.DateTimeValue -> quote(value.value.toString())
            is FilterValue.EnumValue -> quote(value.value)
            is FilterValue.UuidValue -> quote(value.value.toString())
            is FilterValue.JsonValue -> quote(value.value)
            is FilterValue.NullValue -> "null"
        }

    private fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            if (ch == '"' || ch == '\\') append('\\')
            append(ch)
        }
        append('"')
    }
}
