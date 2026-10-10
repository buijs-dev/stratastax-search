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

import dev.buijs.stratastax.search.core.app.codec.HmacBase64CursorCodec
import dev.buijs.stratastax.search.core.app.filter.RsqlFilterParser
import dev.buijs.stratastax.search.core.app.sort.OrderedMultivaluedSortParser
import dev.buijs.stratastax.search.core.spi.CursorElement.StringValue
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SearchParserCursorEdgeCaseTest {

    private val sortParser = OrderedMultivaluedSortParser()
    private val codec = HmacBase64CursorCodec({ "secret".toByteArray(Charsets.UTF_8) })
    private val parser = SearchParser(codec, RsqlFilterParser(), sortParser)

    private fun cursorWith(criteria: CursorCriteria): String =
        codec.encode(CursorState(listOf(StringValue("x")), "hash", criteria = criteria))

    @Test
    fun `a cursor whose sort is unknown to the configuration is an invalid cursor`() {
        val cursor = cursorWith(CursorCriteria("gone:ASC", null))

        assertThatThrownBy { parser.parse(SearchRequest(cursor = cursor), testSearchConfiguration) }
            .isInstanceOf(InvalidCursorException::class.java)
            .hasMessage("Invalid cursor")
            .hasCauseInstanceOf(SearchException::class.java)
    }

    @Test
    fun `a cursor whose sort has no direction is an invalid cursor`() {
        val cursor = cursorWith(CursorCriteria("name", null))

        assertThatThrownBy { parser.parse(SearchRequest(cursor = cursor), testSearchConfiguration) }
            .isInstanceOf(InvalidCursorException::class.java)
    }

    @Test
    fun `a cursor without an issue time never expires`() {
        val state = CursorState(listOf(StringValue("x")), "hash", issuedAt = null)
        val query = SearchQuery(1, sortParser.parse(null, testSearchConfiguration), null, null)
        val undated = state.copy(hash = CriteriaHash.of(query.sort, null))
        val undatedCodec =
            object : dev.buijs.stratastax.search.core.spi.CursorCodec {

                override fun encode(cursor: CursorState) = "undated"

                override fun decode(cursor: String) = undated
            }

        val farFuture = Clock.fixed(Instant.parse("2999-01-01T00:00:00Z"), ZoneOffset.UTC)
        val subject = SearchParser(undatedCodec, RsqlFilterParser(), sortParser, clock = farFuture)

        val result = subject.parse(SearchRequest(cursor = "undated"), testSearchConfiguration)

        assertThat(result.cursor).isEqualTo(undated)
    }

    @Test
    fun `a cursor that cannot be decoded is an invalid cursor`() {
        assertThatThrownBy { parser.parse(SearchRequest(cursor = "!!!"), testSearchConfiguration) }
            .isInstanceOf(InvalidCursorException::class.java)
            .hasMessage("Invalid cursor")
    }
}
