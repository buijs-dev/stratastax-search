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
package dev.buijs.stratastax.search.core.app.filter

import dev.buijs.stratastax.search.core.api.CriteriaHash
import dev.buijs.stratastax.search.core.app.sort.OrderedMultivaluedSortParser
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FilterWriterTest {

    private val parser = RsqlFilterParser()
    private val sort = OrderedMultivaluedSortParser().parse(null, testSearchConfiguration)

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings =
            [
                "name==Alice",
                "name!=Alice",
                "name==null",
                "name!=NULL",
                "name==\"null\"",
                "score=gt=5",
                "score=le=99.95",
                "playedAt=ge=2026-01-01T00:00:00Z",
                "playedAt=lt=2026-01-01T00:00:00.500Z",
                "playedAt=le=2026-01-01",
                "active==true",
                "status=in=(ACTIVE,PENDING)",
                "status=out=(COMPLETED)",
                "name=in=(a,\"b c\",'d')",
                "name=like=\"*john*\"",
                "name=ilike=\"%john%\"",
                "name==\"quote \\\" backslash \\\\ pipe | percent % semi ; comma , (paren)\"",
                "name==\"and\"",
                "name==a=b!c",
                "name==x;score=gt=1",
                "name==x,score=gt=1",
                "name==x;score=gt=1,active==true",
                "name==x;(score=gt=1,active==true)",
                "(name==x,name==y);(score=gt=1,score=lt=0);active==false",
                "((name==x;active==true),score=gt=1);name!=z",
                "NAME==x AND SCORE=gt=1 or Active==TRUE",
            ],
    )
    fun `written filter parses back into the same tree and hash`(query: String) {
        val filter = parser.parse(query, testSearchConfiguration)!!

        val written = FilterWriter.write(filter)
        val reparsed = parser.parse(written, testSearchConfiguration)!!

        assertThat(reparsed).isEqualTo(filter)
        assertThat(CriteriaHash.of(sort, reparsed)).isEqualTo(CriteriaHash.of(sort, filter))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["name==Alice", "status=in=(ACTIVE,PENDING);score=gt=1"])
    fun `writing is stable`(query: String) {
        val once = FilterWriter.write(parser.parse(query, testSearchConfiguration)!!)
        val twice = FilterWriter.write(parser.parse(once, testSearchConfiguration)!!)

        assertThat(twice).isEqualTo(once)
    }
}
