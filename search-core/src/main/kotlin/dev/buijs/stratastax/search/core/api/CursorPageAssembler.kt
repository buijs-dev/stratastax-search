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

import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.CursorKeyExtractor

/**
 * Turns a repository's cursor-agnostic [Page] into a [CursorPage] a service can return.
 *
 * [Page.hasMore] tells whether more items lie in the direction the page was read: after it for a
 * first or next page, before it for a previous page (a [CursorDirection.BEFORE] cursor). The page
 * the request came from always lies on the other side.
 */
class CursorPageAssembler(
    private val cursorCodec: CursorCodec,
) {

    /**
     * Assembles the [CursorPage] for [page], which was read for [search].
     *
     * @param keyExtractor Extracts the sort-key values of an item, used to build its cursor.
     */
    fun <T> assemble(
        search: SearchQuery,
        page: Page<T>,
        keyExtractor: CursorKeyExtractor<T>,
    ): CursorPage<T> {
        val hash = CriteriaHash.of(search.sort, search.filter)
        val cursor = search.cursor
        val criteria = cursor?.criteria ?: CursorCriteria.of(search.sort, search.filter)
        val backwards = cursor?.direction == CursorDirection.BEFORE

        fun encode(
            item: T,
            direction: CursorDirection,
        ) = cursorCodec.encode(
            CursorState(
                values = keyExtractor.elementsFor(item, search.sort),
                hash = hash,
                direction = direction,
                criteria = criteria,
            ),
        )

        val hasNext =
            if (backwards) {
                page.items.isNotEmpty()
            } else {
                page.hasMore
            }

        val hasPrevious =
            if (backwards) {
                page.hasMore
            } else {
                cursor != null && page.items.isNotEmpty()
            }

        val nextCursor =
            if (hasNext) {
                encode(page.items.last(), CursorDirection.AFTER)
            } else {
                null
            }

        val previousCursor =
            if (hasPrevious) {
                encode(page.items.first(), CursorDirection.BEFORE)
            } else {
                null
            }

        val currentCursor = cursor?.let(cursorCodec::encode)
        val items = page.items
        return CursorPage(
            items = items,
            nextCursor = nextCursor,
            previousCursor = previousCursor,
            currentCursor = currentCursor,
        )
    }
}
