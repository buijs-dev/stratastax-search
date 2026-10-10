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
package dev.buijs.stratastax.search.core.app.sort

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.Sort
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.entity.core.api.SortField
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.app.SearchFieldCache
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortParser

private const val DEFAULT_SORT_DIRECTION = "ASC"

/**
 * Default [SortParser] implementation that parses a comma-separated `field:DIRECTION` expression
 * list and guarantees a stable, fully deterministic ordering by appending the configuration's
 * tiebreaker field.
 */
internal class OrderedMultivaluedSortParser : SortParser {

    /**
     * Parses [value] into a [Sort], falling back to [SearchConfiguration.defaultSort] when [value]
     * is `null`, and always appending the configuration's tiebreaker field if it isn't already
     * present.
     *
     * @param value The raw sort expression, or `null` to use the default sort.
     * @param configuration The active [SearchConfiguration].
     * @return The resolved [Sort].
     * @throws SearchException If [value] is non-null but blank, malformed, or invalid.
     */
    override fun parse(
        value: String?,
        configuration: SearchConfiguration,
    ): Sort =
        Sort(
            fields =
                appendTiebreaker(
                    tiebreaker = configuration.tiebreakerField,
                    fields =
                        if (value == null) {
                            configuration.defaultSort
                        } else {
                            parseExplicitSort(value, configuration)
                        },
                ),
        )
}

private fun parseExplicitSort(
    value: String,
    configuration: SearchConfiguration,
): List<SortField> {
    validate(value.isNotBlank()) { "Sort parameter must not be blank" }

    return value
        .split(",")
        .mapIndexed { index, expression ->
            parseField(expression = expression, index = index, configuration = configuration)
        }.also(::ensureNoDuplicateFields)
}

private fun parseField(
    expression: String,
    index: Int,
    configuration: SearchConfiguration,
): SortField {
    validate(expression.isNotBlank()) {
        "Sort expression must not be blank at position ${index + 1}"
    }

    val parts =
        if (expression.contains(":")) {
            expression.split(":", limit = 2)
        } else {
            listOf(expression, DEFAULT_SORT_DIRECTION)
        }

    val fieldName = parts[0].trim()
    validate(fieldName.isNotEmpty()) {
        "Sort field must not be blank at position ${index + 1}"
    }

    val directionValue = parts[1].trim()
    validate(directionValue.isNotEmpty()) {
        "Sort direction must not be blank for field '$fieldName'"
    }

    val field =
        SearchFieldCache.find(fieldName, configuration)
            ?: throw SearchException("Unknown sort field '$fieldName'")

    validate(field.sortable) {
        "Field '$fieldName' is not sortable"
    }

    val direction =
        try {
            SortDirection.of(directionValue, fieldName)
        } catch (e: IllegalArgumentException) {
            throw e.toSearchException("Invalid sort direction '$directionValue'")
        }

    return SortField(field, direction)
}

private fun ensureNoDuplicateFields(fields: List<SortField>) {
    val seen = HashSet<String>()
    for ((field) in fields) {
        val key = field.name.lowercase()
        validate(seen.add(key)) { "Duplicate sort field '${field.name}' in sort expression" }
    }
}

/**
 * Appends [tiebreaker] to [fields] in ascending order, unless [fields] already sorts by it,
 * guaranteeing every [Sort] produces a stable, fully deterministic ordering.
 *
 * `internal` (not `private`) so `SearchQueryDsl.kt`'s own config-aware `search(configuration) { }`
 * overload can reuse this exact rule too, rather than hand-rolling a second copy of it.
 *
 * @param fields The explicit or default sort fields.
 * @param tiebreaker The configuration's designated tiebreaker field.
 * @return [fields] unchanged if it already includes [tiebreaker], otherwise [fields] with the
 *   tiebreaker appended in ascending order.
 */
internal fun appendTiebreaker(
    fields: List<SortField>,
    tiebreaker: EntityField,
): List<SortField> =
    when {
        fields.any { it.field.name.equals(tiebreaker.name, ignoreCase = true) } -> fields
        else -> fields + SortField(tiebreaker, SortDirection.ASC)
    }

internal fun IllegalArgumentException.toSearchException(fallback: String): SearchException =
    SearchException(message ?: fallback, this)
