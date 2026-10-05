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
 * What the repository returns, without any cursor knowledge.
 *
 * @property hasMore Whether more items lie beyond [items] in the direction the page was read.
 */
data class Page<T>(val items: List<T>, val hasMore: Boolean)

/**
 * What the service returns: a [Page] with cursors added.
 *
 * @property nextCursor Cursor for the next page, or `null` if there is none.
 * @property previousCursor Cursor for the previous page, or `null` if there is none.
 * @property currentCursor The cursor this page was requested with, `null` for a first page.
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val previousCursor: String?,
    val currentCursor: String? = null,
)
