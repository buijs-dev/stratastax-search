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
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.api.Sort
import dev.buijs.stratastax.search.core.app.SearchFieldCache
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortDirection.Companion.of
import dev.buijs.stratastax.search.core.spi.SortField
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
    override fun parse(value: String?, configuration: SearchConfiguration): Sort =
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
                )
        )
}

/**
 * Parses a comma-separated sort expression into an ordered list of [SortField]s.
 *
 * @param value The raw sort expression, e.g. `"name:ASC,createdAt:DESC"`.
 * @param configuration The active [SearchConfiguration] used to resolve and validate fields.
 * @return The parsed, order-preserving list of [SortField]s.
 * @throws SearchException If [value] is blank, any individual expression is malformed or invalid,
 *   or the same field is referenced more than once.
 */
private fun parseExplicitSort(value: String, configuration: SearchConfiguration): List<SortField> {
    validate(value.isNotBlank()) { "Sort parameter must not be blank" }
    return value
        .split(",")
        .mapIndexed { index, expression ->
            parseField(expression = expression, index = index, configuration = configuration)
        }
        .also(::ensureNoDuplicateFields)
}

/**
 * Parses a single `field:DIRECTION` sort expression.
 *
 * @param expression The raw expression, e.g. `"name:ASC"`.
 * @param index The zero-based position of [expression] within the overall sort parameter, used for
 *   error reporting.
 * @param configuration The active [SearchConfiguration] used to resolve and validate the field.
 * @return The parsed [SortField].
 * @throws SearchException If [expression] is blank, malformed, or references an unknown or
 *   non-sortable field.
 */
private fun parseField(
    expression: String,
    index: Int,
    configuration: SearchConfiguration,
): SortField {
    validate(expression.isNotBlank()) {
        "Sort expression must not be blank at position ${index + 1}"
    }

    val parts =
        when {
            expression.contains(":") -> expression.split(":", limit = 2)
            else -> listOf(expression, DEFAULT_SORT_DIRECTION)
        }

    validate(parts.size == 2) {
        "Invalid sort expression at position ${index + 1}: '$expression', expected format 'field:ASC' or 'field:DESC'."
    }

    val fieldName = parts[0].trim()
    validate(fieldName.isNotEmpty()) { "Sort field must not be blank at position ${index + 1}" }

    val directionValue = parts[1].trim()
    validate(directionValue.isNotEmpty()) {
        "Sort direction must not be blank for field '$fieldName'"
    }

    val field =
        SearchFieldCache.find(fieldName, configuration)
            ?: throw SearchException("Unknown sort field '$fieldName'")
    validate(field.sortable) { "Field '$fieldName' is not sortable" }

    return SortField(field, of(directionValue, fieldName))
}

/**
 * Ensures no field is referenced more than once (case-insensitively) across [fields].
 *
 * @param fields The parsed sort fields to check.
 * @throws SearchException If the same field appears more than once.
 */
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
internal fun appendTiebreaker(fields: List<SortField>, tiebreaker: EntityField): List<SortField> =
    when {
        fields.any { it.field.name.equals(tiebreaker.name, ignoreCase = true) } -> fields
        else -> fields + SortField(tiebreaker, SortDirection.ASC)
    }
