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
import dev.buijs.stratastax.search.core.app.filter.FilterWriter
import dev.buijs.stratastax.search.core.app.sort.SortWriter
import dev.buijs.stratastax.search.core.spi.CursorElement
import java.time.Instant

/**
 * Decoded pagination state carried by a search cursor.
 *
 * @property values The typed sort-key values of the item the cursor points at, in the same order as
 *   the active [Sort]'s fields. The next query resumes from exactly that position.
 * @property hash Hash of the sort and filter criteria used to generate this cursor, so a paginated
 *   request can be validated against the original criteria.
 * @property direction Whether the page lies after (next page) or before (previous page) [values].
 * @property issuedAt When the cursor was issued, used to expire it. `null` on a state that has not
 *   been encoded yet: the codec stamps it with the current time.
 * @property criteria The criteria this cursor was issued for, `null` for a cursor built without
 *   them.
 */
data class CursorState(
    val values: List<CursorElement>,
    val hash: String,
    val direction: CursorDirection = CursorDirection.AFTER,
    val issuedAt: Instant? = null,
    val criteria: CursorCriteria? = null,
)

/**
 * The sort and filter a cursor was issued for, in canonical form: parsing them again yields the
 * same query, so a request may leave its criteria out and resume with these.
 *
 * @property sort The resolved sort, e.g. `playedAt:DESC,name:ASC`.
 * @property filter The filter in RSQL, or `null` when there was none.
 */
data class CursorCriteria(
    val sort: String,
    val filter: String?,
) {

    internal companion object {

        /** The canonical criteria of an already parsed [sort] and [filter]. */
        fun of(
            sort: Sort,
            filter: Filter?,
        ) = CursorCriteria(SortWriter.write(sort), filter?.let(FilterWriter::write))
    }
}

/** Which side of a cursor's position a page lies on, in the sort order. */
enum class CursorDirection(
    internal val code: String,
) {

    /** The items after the cursor: the next page. */
    AFTER("A"),

    /** The items before the cursor: the previous page. */
    BEFORE("B"),
    ;

    internal companion object {

        fun ofCode(code: String?): CursorDirection? = entries.firstOrNull { it.code == code }
    }
}
