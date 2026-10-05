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

import dev.buijs.stratastax.search.core.app.filter.RsqlFilterParser
import dev.buijs.stratastax.search.core.app.sort.OrderedMultivaluedSortParser
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CriteriaHashTest {

    private val filterParser = RsqlFilterParser()
    private val sortParser = OrderedMultivaluedSortParser()

    private fun hash(sort: String? = null, filter: String? = null) =
        CriteriaHash.of(
            sortParser.parse(sort, testSearchConfiguration),
            filterParser.parse(filter, testSearchConfiguration),
        )

    @Test
    fun `a string containing separators does not hash like a list of values`() {
        assertThat(hash(filter = "name=in=(\"x,S:y\")"))
            .isNotEqualTo(hash(filter = "name=in=(x,y)"))
    }

    @Test
    fun `a string containing a separator does not hash like two comparisons`() {
        assertThat(hash(filter = "name==\"x,name==S:1:y\""))
            .isNotEqualTo(hash(filter = "name==x,name==y"))
    }

    @Test
    fun `a like pattern is length prefixed`() {
        assertThat(hash(filter = "name=like=\"a*\""))
            .isNotEqualTo(hash(filter = "name=like=\"a**\""))
    }

    @Test
    fun `equal criteria hash alike regardless of casing and connector notation`() {
        assertThat(hash("PLAYEDAT:desc", "name==x;active==true"))
            .isEqualTo(hash("playedAt:DESC", "NAME==x and ACTIVE==TRUE"))
    }

    @Test
    fun `different criteria hash differently`() {
        assertThat(hash("playedAt:ASC")).isNotEqualTo(hash("playedAt:DESC"))
        assertThat(hash(filter = "name==x")).isNotEqualTo(hash(filter = "name==y"))
        assertThat(hash(filter = "name==x")).isNotEqualTo(hash())
    }
}
