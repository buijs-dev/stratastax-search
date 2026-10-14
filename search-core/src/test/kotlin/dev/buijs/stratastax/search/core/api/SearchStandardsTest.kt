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

import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SearchStandardsTest {

    @Test
    fun `standard filter parser parses RSQL`() {
        val filter = standardFilterParser().parse("name==Alain", testSearchConfiguration)

        assertThat(filter).isNotNull
        assertThat(filter!!.root).isInstanceOf(ComparisonExpression::class.java)
    }

    @Test
    fun `standard filter parser honors the maximum nesting depth`() {
        val parser = standardFilterParser(maxNestingDepth = 1)

        assertThat(parser.parse("(name==a)", testSearchConfiguration)).isNotNull
        assertThatThrownBy { parser.parse("((name==a))", testSearchConfiguration) }
            .isInstanceOf(SearchException::class.java)
            .hasMessage("Exceeded maximum filter nesting depth of 1.")
    }

    @Test
    fun `standard sort parser falls back to the default sort`() {
        val sort = standardSortParser().parse(null, testSearchConfiguration)

        assertThat(sort.fields).isEqualTo(testSearchConfiguration.defaultSort)
    }

    @Test
    fun `standard sort parser appends the tiebreaker`() {
        val sort = standardSortParser().parse("score:DESC", testSearchConfiguration)

        assertThat(sort.fields.map { it.field.name }).containsExactly("score", nameField.name)
    }

    @Test
    fun `standard cursor codec round-trips a cursor`() {
        val codec = standardCursorCodec({ "secret".toByteArray() })
        val state = CursorState(listOf(CursorElement.StringValue("x")), "hash")

        val decoded = codec.decode(codec.encode(state))

        assertThat(decoded).isEqualTo(state.copy(issuedAt = decoded!!.issuedAt))
    }

    @Test
    fun `standard cursor codec rejects a cursor of another version`() {
        val signingKey = CursorSigningKey { "secret".toByteArray() }

        val v1 = standardCursorCodec(signingKey)
        val v2 = standardCursorCodec(signingKey, version = "2")

        val cursor = v1.encode(CursorState(listOf(CursorElement.StringValue("x")), "hash"))

        assertThat(v2.decode(cursor)).isNull()
        assertThat(v1.decode(cursor)).isNotNull
    }
}
