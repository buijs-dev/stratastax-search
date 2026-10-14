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

import dev.buijs.stratastax.entity.core.api.Sort
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.entity.core.api.SortField
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorKeyExtractor
import dev.buijs.stratastax.search.testdata.scoreField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CursorPageAssemblerTest {

    /** Encodes a cursor readably: `A:b` is the page after item b, `B:a` the page before item a. */
    private val codec =
        object : CursorCodec {

            override fun decode(cursor: String): CursorState? = null

            override fun encode(cursor: CursorState): String =
                "${cursor.direction.name.first()}:" +
                    (cursor.values.single() as CursorElement.StringValue).value
        }

    private val keys =
        CursorKeyExtractor<String> { item, _ -> listOf(CursorElement.StringValue(item)) }

    private val sort = Sort(listOf(SortField(scoreField, SortDirection.ASC)))

    @ParameterizedTest(name = "{0} cursor, hasMore={1} -> next={2}, previous={3}")
    @CsvSource(
        "NONE,   true,  A:b, ",
        "NONE,   false, ,    ",
        "AFTER,  true,  A:b, B:a",
        "AFTER,  false, ,    B:a",
        "BEFORE, true,  A:b, B:a",
        "BEFORE, false, A:b, ",
    )
    fun `links the pages on either side of the page that was read`(
        cursor: String,
        hasMore: Boolean,
        next: String?,
        previous: String?,
    ) {
        val search =
            SearchQuery(
                limit = 2,
                sort = sort,
                filter = null,
                cursor =
                    if (cursor == "NONE") {
                        null
                    } else {
                        CursorState(
                            listOf(CursorElement.StringValue("x")),
                            "hash",
                            CursorDirection.valueOf(cursor),
                        )
                    },
            )

        val page =
            CursorPageAssembler(codec).assemble(search, Page(listOf("a", "b"), hasMore), keys)

        assertThat(page.nextCursor).isEqualTo(next)
        assertThat(page.previousCursor).isEqualTo(previous)
    }

    @Test
    fun `every cursor carries the criteria of the query`() {
        val criteriaCodec = recordingCodec()
        val search = SearchQuery(limit = 2, sort = sort, filter = null, cursor = null)

        CursorPageAssembler(criteriaCodec.codec)
            .assemble(search, Page(listOf("a", "b"), true), keys)

        assertThat(criteriaCodec.encoded).isNotEmpty()
        assertThat(criteriaCodec.encoded.map { it.criteria })
            .containsOnly(CursorCriteria("score:ASC", null))
    }

    @Test
    fun `criteria of the request cursor are reused`() {
        val criteriaCodec = recordingCodec()
        val carried = CursorCriteria("score:DESC", "name==x")
        val search =
            SearchQuery(
                limit = 2,
                sort = sort,
                filter = null,
                cursor =
                    CursorState(listOf(CursorElement.StringValue("x")), "hash", criteria = carried),
            )

        CursorPageAssembler(criteriaCodec.codec)
            .assemble(search, Page(listOf("a", "b"), true), keys)

        assertThat(criteriaCodec.encoded.map { it.criteria }).containsOnly(carried)
    }

    private class RecordingCodec {

        val encoded = mutableListOf<CursorState>()
        val codec =
            object : CursorCodec {

                override fun decode(cursor: String): CursorState? = null

                override fun encode(cursor: CursorState): String {
                    encoded += cursor
                    return "c"
                }
            }
    }

    private fun recordingCodec() = RecordingCodec()

    @ParameterizedTest(name = "{0} cursor -> current={1}")
    @CsvSource("NONE, ", "AFTER, A:x", "BEFORE, B:x")
    fun `current cursor is the cursor the page was requested with`(
        cursor: String,
        current: String?,
    ) {
        val search =
            SearchQuery(
                limit = 2,
                sort = sort,
                filter = null,
                cursor =
                    if (cursor == "NONE") {
                        null
                    } else {
                        CursorState(
                            listOf(CursorElement.StringValue("x")),
                            "hash",
                            CursorDirection.valueOf(cursor),
                        )
                    },
            )

        val page = CursorPageAssembler(codec).assemble(search, Page(listOf("a", "b"), true), keys)

        assertThat(page.currentCursor).isEqualTo(current)
    }

    @ParameterizedTest(name = "{0} cursor, empty page -> no links")
    @CsvSource("NONE", "AFTER", "BEFORE")
    fun `an empty page has no neighbours, whatever the direction`(cursor: String) {
        val search =
            SearchQuery(
                limit = 2,
                sort = sort,
                filter = null,
                cursor =
                    if (cursor == "NONE") {
                        null
                    } else {
                        CursorState(
                            listOf(CursorElement.StringValue("x")),
                            "hash",
                            CursorDirection.valueOf(cursor),
                        )
                    },
            )

        val page = CursorPageAssembler(codec).assemble(search, Page(emptyList(), false), keys)

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
        assertThat(page.previousCursor).isNull()
    }
}
