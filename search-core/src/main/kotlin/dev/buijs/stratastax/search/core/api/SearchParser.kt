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

import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.app.valueOrThrow
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.FilterParser
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortParser

class SearchParser(
    private val cursorCodec: CursorCodec,
    private val filterParser: FilterParser,
    private val sortParser: SortParser,
) {

    fun parse(request: SearchRequest, configuration: SearchConfiguration): SearchQuery {
        val limit = request.limit ?: configuration.defaultLimit
        validate(limit <= configuration.maxLimit) { "Limit exceeds maximum allowed" }

        val sort = sortParser.parse(value = request.criteria?.sort, configuration = configuration)

        val filter =
            filterParser.parse(value = request.criteria?.filter, configuration = configuration)

        val cursor =
            request.cursor?.let {
                val decoded = valueOrThrow(cursorCodec.decode(it)) { "Invalid cursor" }
                validate(decoded.criteriaHash == CriteriaHash.of(sort, filter)) {
                    "Cursor does not match the current sort/filter criteria"
                }
                decoded
            }

        return SearchQuery(limit = limit, sort = sort, filter = filter, cursor = cursor)
    }
}
