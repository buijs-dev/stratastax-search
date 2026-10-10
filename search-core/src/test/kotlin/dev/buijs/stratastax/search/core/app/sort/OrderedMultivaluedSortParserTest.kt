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
package dev.buijs.stratastax.search.core.app.sort

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.testdata.activeField
import dev.buijs.stratastax.search.testdata.internalNotesField
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.nameSortField
import dev.buijs.stratastax.search.testdata.playedAtField
import dev.buijs.stratastax.search.testdata.scoreField
import dev.buijs.stratastax.search.testdata.statusSortField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OrderedMultivaluedSortParserTest {

    private val parser = OrderedMultivaluedSortParser()

    private fun parse(value: String?) = parser.parse(value, testSearchConfiguration).fields

    private fun names(value: String?) = parse(value).map { it.field.name to it.direction }

    @Nested
    inner class Defaults {

        @Test
        fun `null falls back to the default sort`() {
            assertThat(parse(null)).containsExactly(nameSortField, statusSortField)
        }
    }

    @Nested
    inner class Parsing {

        @Test
        fun `a single field is parsed and the tiebreaker is appended`() {
            assertThat(names("score:DESC"))
                .containsExactly(
                    scoreField.name to SortDirection.DESC,
                    nameField.name to SortDirection.ASC,
                )
        }

        @Test
        fun `the direction defaults to ASC`() {
            assertThat(names("score")).first().isEqualTo(scoreField.name to SortDirection.ASC)
        }

        @Test
        fun `field names and directions are case-insensitive`() {
            assertThat(names("SCORE:desc")).first().isEqualTo(scoreField.name to SortDirection.DESC)
        }

        @Test
        fun `multiple fields keep their order`() {
            assertThat(names("playedAt:DESC,score:ASC,name:DESC"))
                .containsExactly(
                    playedAtField.name to SortDirection.DESC,
                    scoreField.name to SortDirection.ASC,
                    nameField.name to SortDirection.DESC,
                )
        }

        @Test
        fun `an explicit tiebreaker is not appended twice`() {
            assertThat(parse("name:DESC")).hasSize(1)
        }

        @Test
        fun `whitespace around field and direction is ignored`() {
            assertThat(names(" score : DESC ,name"))
                .containsExactly(
                    scoreField.name to SortDirection.DESC,
                    nameField.name to SortDirection.ASC,
                )
        }
    }

    @Nested
    inner class Errors {

        @ParameterizedTest
        @ValueSource(strings = ["", "   "])
        fun `a blank sort is rejected`(value: String) {
            assertThatThrownBy { parse(value) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Sort parameter must not be blank")
        }

        @Test
        fun `a blank expression is rejected with its position`() {
            assertThatThrownBy { parse("score,,name") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Sort expression must not be blank at position 2")
            assertThatThrownBy { parse("score,") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Sort expression must not be blank at position 2")
        }

        @Test
        fun `a blank field is rejected`() {
            assertThatThrownBy { parse(" :ASC") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Sort field must not be blank at position 1")
        }

        @Test
        fun `a blank direction is rejected`() {
            assertThatThrownBy { parse("score: ") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Sort direction must not be blank for field 'score'")
        }

        @Test
        fun `an unknown direction is rejected`() {
            assertThatThrownBy { parse("score:UP") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage(
                    "Invalid sort direction 'UP' for field 'score', expected 'ASC' or 'DESC'",
                )
        }

        @Test
        fun `an unknown field is rejected`() {
            assertThatThrownBy { parse("nope:ASC") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Unknown sort field 'nope'")
        }

        @Test
        fun `a field that is not sortable is rejected`() {
            assertThatThrownBy { parse("${internalNotesField.name}:ASC") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Field 'internalNotes' is not sortable")
            assertThatThrownBy { parse("${activeField.name}:ASC") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Field 'active' is not sortable")
        }

        @Test
        fun `a field referenced twice is rejected, whatever its casing`() {
            assertThatThrownBy { parse("score:ASC,SCORE:DESC") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Duplicate sort field 'score' in sort expression")
        }
    }

    @Nested
    inner class Tiebreaker {

        @Test
        fun `appendTiebreaker leaves fields that already contain it alone, whatever its casing`() {
            val fields =
                listOf(nameSortField.copy(field = EntityField("NAME", EntityFieldType.STRING)))

            assertThat(appendTiebreaker(fields, nameField)).isEqualTo(fields)
        }

        @Test
        fun `appendTiebreaker adds it ascending otherwise`() {
            val fields = parse("score:DESC")

            assertThat(appendTiebreaker(fields.dropLast(1), nameField).last())
                .isEqualTo(nameSortField)
        }
    }
}
