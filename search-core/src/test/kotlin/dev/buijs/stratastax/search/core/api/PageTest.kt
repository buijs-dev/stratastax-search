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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PageTest {

    @Test
    fun `page holds items and the has-more flag`() {
        val page = Page(listOf("a", "b"), hasMore = true)

        assertThat(page.items).containsExactly("a", "b")
        assertThat(page.hasMore).isTrue()
        assertThat(page).isEqualTo(Page(listOf("a", "b"), true))
    }

    @Test
    fun `cursor page has no current cursor by default`() {
        val page = CursorPage(listOf(1), nextCursor = "n", previousCursor = null)

        assertThat(page.currentCursor).isNull()
        assertThat(page.nextCursor).isEqualTo("n")
        assertThat(page.previousCursor).isNull()
    }

    @Test
    fun `cursor page keeps the cursor it was requested with`() {
        val page =
            CursorPage(listOf(1), nextCursor = null, previousCursor = "p", currentCursor = "c")

        assertThat(page.currentCursor).isEqualTo("c")
        assertThat(page.copy(items = emptyList()).items).isEmpty()
    }

    @Test
    fun `exceptions expose their message and cause`() {
        val cause = IllegalStateException("boom")

        assertThat(SearchException("m", cause).cause).isSameAs(cause)
        assertThat(InvalidCursorException("bad", cause))
            .isInstanceOf(SearchException::class.java)
            .hasMessage("bad")
            .hasCause(cause)
    }
}
