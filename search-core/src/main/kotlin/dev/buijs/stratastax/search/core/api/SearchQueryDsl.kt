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
import dev.buijs.stratastax.search.core.app.sort.appendTiebreaker
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.STANDARD_DEFAULT_LIMIT
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortField

/**
 * Builds a [SearchQuery] programmatically from typed [EntityField] references (e.g. a generated
 * `XxxSearch` bean's properties) - for tests and other non-HTTP callers that already have
 * well-typed values in hand, as an alternative to `SearchParser`'s RSQL-string parsing path.
 * Deliberately independent of any `SearchConfiguration` - no default-limit/default-sort fallback,
 * no config-driven validation:
 * ```
 * val search = search {
 *     limit = 10
 *     sort { displayName.asc() }
 *     filter { owner eq "Alain" }
 *     after(cursor) // a CursorState, e.g. decoded via CursorCodec
 * }
 * ```
 *
 * See the [configuration]-accepting overload below for a hand-written production call site (as
 * opposed to a test) that wants the same default-limit/default-sort/tiebreaker fallback
 * `SearchParser` itself applies, without separately calling `SortParser` or duplicating
 * [SearchConfiguration.defaultSort]/[SearchConfiguration.tiebreakerField] logic by hand.
 */
fun search(block: SearchQueryBuilder.() -> Unit): SearchQuery =
    SearchQueryBuilder().apply(block).build()

/**
 * Same as [search], but resolves an unset [SearchQueryBuilder.limit] from [configuration]'s own
 * [SearchConfiguration.defaultLimit], and an unset sort from [SearchConfiguration.defaultSort] -
 * with [SearchConfiguration.tiebreakerField] appended if it isn't already present, exactly like
 * `SearchParser`'s own `SortParser` step does - instead of leaving `limit`/`sort` at this DSL's own
 * config-independent defaults (see [search]'s own kdoc). For a call site that already has the
 * entity's generated `${Entity}SearchConfiguration` injected anyway (e.g. alongside
 * `${Entity}ReadAccess`/`SearchParser`) and wants a `SearchQuery` built directly, e.g. for a
 * `count(...)` precondition check that has no need to also round-trip through a `SearchRequest`:
 * ```
 * val search = search(matchSearchConfiguration) {
 *     filter { and(id eq matchId.value, lockedAt.isNotNull()) }
 * }
 * ```
 */
fun search(configuration: SearchConfiguration, block: SearchQueryBuilder.() -> Unit): SearchQuery =
    SearchQueryBuilder(configuration).apply(block).build()

@SearchQueryDsl
class SearchQueryBuilder
internal constructor(private val configuration: SearchConfiguration? = null) {

    var limit: Int = configuration?.defaultLimit ?: STANDARD_DEFAULT_LIMIT

    private val sortFields = mutableListOf<SortField>()
    private var filterExpression: FilterExpression? = null
    private var cursorState: CursorState? = null
    private var pendingCursorValues: List<CursorElement>? = null

    /**
     * Not calling this at all leaves the sort empty - resolved in [build] per [configuration]'s own
     * presence: `null` (the plain [search] overload) means [Sort]'s own `init { }` throws with its
     * existing "must contain at least one field" message, this DSL just inheriting that invariant
     * rather than duplicating it; non-`null` (the [configuration]-accepting [search] overload)
     * falls back to [SearchConfiguration.defaultSort] instead (tiebreaker included) - see that
     * overload's own kdoc.
     */
    fun sort(block: SortBuilder.() -> Unit) {
        sortFields += SortBuilder().apply(block).fields
    }

    /**
     * [block]'s own return value becomes the filter's root expression - combine multiple conditions
     * with [and]/[or].
     */
    fun filter(block: () -> FilterExpression) {
        filterExpression = block()
    }

    /** An already-decoded cursor, e.g. from `cursorCodec.decode(cursorString)`. */
    fun after(cursor: CursorState) {
        cursorState = cursor
    }

    /**
     * Convenience overload: builds the [CursorState] for you, computing its `criteriaHash` from
     * this query's own eventual [sort]/[filter] (at [build] time, so calling this before or after
     * `sort { }`/`filter { }` makes no difference) - avoids hand-computing `CriteriaHash.of(sort,
     * filter)` yourself and risking it drifting from the actual query.
     */
    fun after(vararg values: CursorElement) {
        pendingCursorValues = values.toList()
    }

    internal fun build(): SearchQuery {
        validate(limit > 0) { "limit must be greater than 0, was $limit" }
        val sort =
            when (val config = configuration) {
                null -> Sort(sortFields)
                else ->
                    Sort(
                        appendTiebreaker(
                            sortFields.ifEmpty { config.defaultSort },
                            config.tiebreakerField,
                        )
                    )
            }
        val filter = filterExpression?.let { Filter(it) }
        val cursor =
            cursorState
                ?: pendingCursorValues?.let { CursorState(it, CriteriaHash.of(sort, filter)) }
        return SearchQuery(limit = limit, sort = sort, filter = filter, cursor = cursor)
    }
}

@SearchQueryDsl
class SortBuilder internal constructor() {

    internal val fields = mutableListOf<SortField>()

    /**
     * Shadows the top-level `EntityField.asc()`/`desc()` ([Sort.kt]) inside a `sort { }` block -
     * same names, but these register into [fields] instead of just returning a value, so `sort {
     * displayName.asc() }` (and multiple statements, e.g. `sort { displayName.asc(); id.asc() }`)
     * both work with no other ceremony - Kotlin resolves a member extension declared on the block's
     * own receiver over a same-named top-level one.
     */
    fun EntityField.asc() {
        fields += SortField(this, SortDirection.ASC)
    }

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
