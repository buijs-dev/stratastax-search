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
package dev.buijs.stratastax.search.core.spi

import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.api.Sort

/**
 * Extracts a row's position in the current sort order as a list of CursorElement, one per Sort
 * field, IN THE SAME ORDER as sort.fields. CursorState.values are positional, not keyed by name.
 */
fun interface CursorKeyExtractor<T> {
    fun elementsFor(item: T, sort: Sort): List<CursorElement>
}

fun <T> Map<String, (T) -> CursorElement>.filterElementsFor(
    item: T,
    sort: Sort,
): List<CursorElement> =
    sort.fields
        .map { it.field.name }
        .map {
            this[it]
                ?: throw SearchException(
                    "No cursorValue extractor configured for sortable field '$it'. " +
                        "This should have been caught when the configuration was built. " +
                        "Did the Sort come from a different configuration?"
                )
        }
        .map { it(item) }
