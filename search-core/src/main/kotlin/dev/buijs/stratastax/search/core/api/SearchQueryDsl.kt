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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.Sort
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.entity.core.api.SortField
import dev.buijs.stratastax.search.core.app.sort.appendTiebreaker
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.STANDARD_DEFAULT_LIMIT
import dev.buijs.stratastax.search.core.spi.SearchConfiguration

/**
 * Builds a [SearchQuery] programmatically from typed [EntityField] references, for tests and other
 * non-HTTP callers. Independent of any [SearchConfiguration]: no default sort and no tiebreaker, so
 * a `sort { }` block is required.
 *
 * ```
 * val search = search {
 *     limit = 10
 *     sort { displayName.asc() }
 *     filter { owner eq "Alain" }
 *     after(cursor)
 * }
 * ```
 */
fun search(block: SearchQueryBuilder.() -> Unit): SearchQuery =
    SearchQueryBuilder().apply(block).build()

/**
 * Same as [search], but an unset limit and sort fall back to [configuration]'s defaults, with its
 * tiebreaker appended to the sort when missing, like [SearchParser] does.
 *
 * ```
 * val search = search(matchSearchConfiguration) {
 *     filter { and(id eq matchId.value, lockedAt.isNotNull()) }
 * }
 * ```
 */
fun search(
    configuration: SearchConfiguration,
    block: SearchQueryBuilder.() -> Unit,
): SearchQuery = SearchQueryBuilder(configuration).apply(block).build()

/** Receiver of the [search] DSL. */
@SearchQueryDsl
class SearchQueryBuilder
    internal constructor(
        private val configuration: SearchConfiguration? = null,
    ) {

        /** Requested page size; must be greater than 0. */
        var limit: Int = configuration?.defaultLimit ?: STANDARD_DEFAULT_LIMIT

        private val sortFields = mutableListOf<SortField>()
        private var filterExpression: FilterExpression? = null
        private var cursorState: CursorState? = null
        private var pendingCursorValues: List<CursorElement>? = null
        private var pendingCursorDirection: CursorDirection = CursorDirection.AFTER

        /**
         * Declares the sort. Leaving it out is an error for the plain [search] overload, and falls back
         * to [SearchConfiguration.defaultSort] for the configuration overload.
         */
        fun sort(block: SortBuilder.() -> Unit) {
            sortFields += SortBuilder().apply(block).fields
        }

        /** Sets the filter to the expression [block] returns; combine conditions with [and]/[or]. */
        fun filter(block: () -> FilterExpression) {
            filterExpression = block()
        }

        /** Resumes after an already-decoded [cursor]. */
        fun after(cursor: CursorState) {
            cursorState = cursor
        }

        /**
         * Resumes after the position described by [values], one per sort field. The cursor's criteria
         * hash is computed at build time from this query's own sort and filter.
         */
        fun after(vararg values: CursorElement) {
            pendingCursorValues = values.toList()
            pendingCursorDirection = CursorDirection.AFTER
        }

        /** Like [after], for the page before the position [values] describe. */
        fun before(vararg values: CursorElement) {
            pendingCursorValues = values.toList()
            pendingCursorDirection = CursorDirection.BEFORE
        }

        internal fun build(): SearchQuery {
            validate(limit > 0) { "limit must be greater than 0, was $limit" }

            val fields =
                when (val config = configuration) {
                    null -> {
                        sortFields
                    }
                    else -> {
                        appendTiebreaker(
                            sortFields.ifEmpty { config.defaultSort },
                            config.tiebreakerField,
                        )
                    }
                }

            validate(fields.isNotEmpty()) {
                "Sort must contain at least one field; results are always sorted."
            }

            val sort = Sort(fields)
            val filter = filterExpression?.let { Filter(it) }

            val cursor =
                cursorState
                    ?: pendingCursorValues?.let {
                        CursorState(
                            it,
                            CriteriaHash.of(sort, filter),
                            pendingCursorDirection,
                            criteria = CursorCriteria.of(sort, filter),
                        )
                    }

            return SearchQuery(limit = limit, sort = sort, filter = filter, cursor = cursor)
        }
    }

/** Receiver of the `sort { }` block. */
@SearchQueryDsl
class SortBuilder internal constructor() {

    internal val fields = mutableListOf<SortField>()

    /**
     * Adds this field ascending. Shadows the top-level `EntityField.asc()` inside `sort { }` so
     * that it registers the field instead of just returning a value.
     */
    fun EntityField.asc() {
        fields += SortField(this, SortDirection.ASC)
    }

    /** Adds this field descending. */
    fun EntityField.desc() {
        fields += SortField(this, SortDirection.DESC)
    }
}

/**
 * All of [expressions]: an [AndExpression], or the expression itself when there is only one - so a
 * filter built from a variable number of conditions needs no special case.
 *
 * @throws SearchException without any expression.
 */
fun and(vararg expressions: FilterExpression): FilterExpression =
    combine(expressions, "and", ::AndExpression)

/** Any of [expressions]: an [OrExpression], or the expression itself when there is only one. */
fun or(vararg expressions: FilterExpression): FilterExpression =
    combine(expressions, "or", ::OrExpression)

private fun combine(
    expressions: Array<out FilterExpression>,
    name: String,
    expression: (List<FilterExpression>) -> FilterExpression,
): FilterExpression =
    when (expressions.size) {
        0 -> throw SearchException("$name(...) requires at least one expression.")
        1 -> expressions.single()
        else -> expression(expressions.toList())
    }
