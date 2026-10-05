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

import dev.buijs.stratastax.search.core.api.Sort

/** Parser interface for converting a raw sort query string into a structured [Sort] instance. */
interface SortParser {

    /**
     * Parses an optional raw sort expression according to the provided search configuration.
     *
     * @param value The raw sort expression (e.g. `"name:ASC,createdAt:DESC"`), or `null` to use the
     *   configuration's default sort.
     * @param configuration The [SearchConfiguration] defining known and sortable fields, plus the
     *   tiebreaker field appended to every result.
     * @return A [Sort] containing the resolved list of
     *   [dev.buijs.stratastax.search.core.spi.SortField]s, always ending with the tiebreaker field.
     * @throws dev.buijs.stratastax.search.core.api.SearchException If [value] is blank, malformed,
     *   references an unknown or non-sortable field, or contains duplicate fields.
     */
    fun parse(value: String?, configuration: SearchConfiguration): Sort
}
