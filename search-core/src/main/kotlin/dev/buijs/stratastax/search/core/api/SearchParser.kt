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

import dev.buijs.stratastax.search.core.app.sort.SortWriter
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.FilterParser
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortParser
import java.time.Clock
import java.time.Duration

/**
 * Turns a raw [SearchRequest] into a validated [SearchQuery].
 *
 * @property cursorExpiry How long a cursor stays valid after it was issued.
 * @property maxFilterLength Longest filter, in characters, a request may carry.
 * @property maxCursorLength Longest cursor, in characters, that is even attempted to decode.
 */
class SearchParser(
    private val cursorCodec: CursorCodec,
    private val filterParser: FilterParser,
    private val sortParser: SortParser,
    private val cursorExpiry: Duration = DEFAULT_CURSOR_EXPIRY,
    private val clock: Clock = Clock.systemUTC(),
    private val maxFilterLength: Int = DEFAULT_MAX_FILTER_LENGTH,
    private val maxCursorLength: Int = DEFAULT_MAX_CURSOR_LENGTH,
) {

    /**
     * Parses [request] against [configuration].
     *
     * Without criteria in the request, a cursor resumes with the sort and filter it was issued for.
     * Criteria in the request replace them, and must then hash to the same value as the cursor.
     *
     * @throws InvalidCursorException If the cursor is malformed, expired, or does not match the
     *   criteria.
     * @throws SearchException If the limit, filter or sort is invalid.
     */
    fun parse(request: SearchRequest, configuration: SearchConfiguration): SearchQuery {
        val limit = request.limit ?: configuration.defaultLimit
        validate(limit >= 1) { "Limit must be at least 1" }
        validate(limit <= configuration.maxLimit) { "Limit exceeds maximum allowed" }

        request.criteria?.filter?.let {
            validate(it.length <= maxFilterLength) { "Filter exceeds maximum length" }
        }

        val decoded = request.cursor?.let(::decodeCursor)

        // Without any criteria in the request a cursor resumes with the criteria it was issued for.
        val criteria =
            decoded?.criteria?.takeIf {
                request.criteria == null ||
                    (request.criteria.sort == null && request.criteria.filter == null)
            }

        val sort =
            if (criteria != null) {
                readCursorSort(criteria.sort, configuration)
            } else {
                sortParser.parse(value = request.criteria?.sort, configuration = configuration)
            }
        val filter =
            filterParser.parse(
                value = if (criteria != null) criteria.filter else request.criteria?.filter,
                configuration = configuration,
            )

        if (decoded != null && decoded.hash != CriteriaHash.of(sort, filter)) {
            throw InvalidCursorException("Cursor does not match the current sort/filter criteria")
        }

        return SearchQuery(limit = limit, sort = sort, filter = filter, cursor = decoded)
    }

    // The sort of a cursor that no longer matches the configuration is just an invalid cursor.
    private fun readCursorSort(text: String, configuration: SearchConfiguration): Sort =
        try {
            SortWriter.read(text, configuration)
        } catch (e: SearchException) {
            throw InvalidCursorException("Invalid cursor", e)
        }

    private fun decodeCursor(cursor: String): CursorState {
        if (cursor.length > maxCursorLength) {
            throw InvalidCursorException("Invalid cursor")
        }
        val decoded = cursorCodec.decode(cursor) ?: throw InvalidCursorException("Invalid cursor")
        if (isExpired(decoded)) {
            throw InvalidCursorException("Cursor has expired")
        }
        return decoded
    }

    private fun isExpired(cursor: CursorState): Boolean {
        val issuedAt = cursor.issuedAt ?: return false
        return clock.instant().isAfter(issuedAt.plus(cursorExpiry))
    }

    companion object {
        /** How long a cursor stays valid after it was issued. */
        val DEFAULT_CURSOR_EXPIRY: Duration = Duration.ofHours(24)

        /** Longest filter, in characters, a request may carry; cursors embed their filter. */
        const val DEFAULT_MAX_FILTER_LENGTH = 1000

        /** Longest cursor, in characters, that is even attempted to decode. */
        const val DEFAULT_MAX_CURSOR_LENGTH = 4096
    }
}
