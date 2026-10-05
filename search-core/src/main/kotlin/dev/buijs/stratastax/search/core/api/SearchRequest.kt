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

/**
 * The raw, unvalidated search input of a client request.
 *
 * @property limit Requested page size, or `null` for the configuration's default.
 * @property criteria Raw sort and filter. If omitted, a [cursor] resumes with its own criteria.
 * @property cursor Opaque cursor from a previous page, or `null` for a first page.
 */
data class SearchRequest(
    val limit: Int? = null,
    val criteria: SearchCriteria? = null,
    val cursor: String? = null,
)

/**
 * Raw sort (`field:DIRECTION,...`) and filter (RSQL) strings of a request.
 *
 * @property sort Sort expression, or `null` for the configuration's default.
 * @property filter RSQL filter, or `null` for none.
 */
data class SearchCriteria(val sort: String? = null, val filter: String? = null)
