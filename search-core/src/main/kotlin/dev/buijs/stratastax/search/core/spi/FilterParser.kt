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

import dev.buijs.stratastax.search.core.api.Filter

/** Parser interface for converting raw filter query strings into structured [Filter] instances. */
interface FilterParser {

    /**
     * Parses an optional raw filter query string according to the provided search configuration.
     *
     * @param value The raw filter query string to parse, or `null`.
     * @param configuration The [SearchConfiguration] defining known and filterable fields.
     * @return A parsed [Filter] object containing the expression tree, or `null` if the input is
     *   null, empty, or blank.
     * @throws dev.buijs.stratastax.search.core.api.SearchException If the query string contains
     *   syntax errors, unknown fields, or invalid operators.
     */
    fun parse(
        value: String?,
        configuration: SearchConfiguration,
    ): Filter?
}
