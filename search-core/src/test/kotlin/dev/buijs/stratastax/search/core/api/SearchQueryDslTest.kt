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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import dev.buijs.stratastax.entity.core.api.Sort
import dev.buijs.stratastax.entity.core.api.SortDirection
import dev.buijs.stratastax.entity.core.api.SortField
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.STANDARD_DEFAULT_LIMIT
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.nameSortField
import dev.buijs.stratastax.search.testdata.scoreField
import dev.buijs.stratastax.search.testdata.statusSortField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class SearchQueryDslTest {

    private val displayName = EntityField("displayName", EntityFieldType.STRING)
    private val owner = EntityField("owner", EntityFieldType.STRING)
    private val id = EntityField("id", EntityFieldType.STRING)

    @Test
    fun `search builds a SearchQuery from limit sort filter and after`() {
        val cursor = CursorState(listOf(CursorElement.StringValue("x")), "hash")

        val query =
            search {
                limit = 10
                sort { displayName.asc() }

                filter { owner eq "Alain" }

                after(cursor)
            }

        assertThat(query.limit).isEqualTo(10)
        assertThat(query.sort).isEqualTo(Sort(listOf(SortField(displayName, SortDirection.ASC))))
        assertThat(query.filter)
            .isEqualTo(
                Filter(
                    ComparisonExpression(
                        owner,
                        ComparisonOperator.EQUALS,
                        FilterValue.StringValue("Alain"),
                    ),
                ),
            )
        assertThat(query.cursor).isEqualTo(cursor)
    }

    @Test
    fun `limit defaults to STANDARD_DEFAULT_LIMIT when not set`() {
        val query = search { sort { id.asc() } }

        assertThat(query.limit).isEqualTo(STANDARD_DEFAULT_LIMIT)
    }

    @Test
    fun `limit can be read and adjusted inside the builder`() {
        val query =
            search(testSearchConfiguration) {
                limit *= 2
                sort { id.asc() }
            }

        assertThat(query.limit).isEqualTo(testSearchConfiguration.defaultLimit * 2)
    }

    @Test
    fun `limit of zero or less throws`() {
        assertThatThrownBy {
            search {
                limit = 0
                sort { id.asc() }
            }
        }.isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `sort never called throws, via Sort's own invariant`() {
        assertThatThrownBy { search {} }
            .isInstanceOf(SearchException::class.java)
            .hasMessageContaining("must contain at least one field")
    }

    @Test
    fun `search(configuration) without an explicit sort falls back to defaultSort`() {
        val query = search(testSearchConfiguration) { filter { owner eq "Alain" } }

        // testSearchConfiguration's own defaultSort already sorts by its
        // tiebreakerField (nameField) - appendTiebreaker is a no-op here.
        assertThat(query.sort).isEqualTo(Sort(listOf(nameSortField, statusSortField)))
    }

    @Test
    fun `search(configuration) with an explicit sort still appends the tiebreaker if it's missing`() {
        val query = search(testSearchConfiguration) { sort { scoreField.desc() } }

        assertThat(query.sort)
            .isEqualTo(
                Sort(
                    listOf(
                        SortField(scoreField, SortDirection.DESC),
                        SortField(nameField, SortDirection.ASC),
                    ),
                ),
            )
    }

    @Test
    fun `search(configuration) without an explicit limit falls back to configuration defaultLimit`() {
        val query = search(testSearchConfiguration) { sort { id.asc() } }

        assertThat(query.limit).isEqualTo(testSearchConfiguration.defaultLimit)
    }

    @Test
    fun `search(configuration) still lets an explicit limit and sort override the defaults`() {
        val query =
            search(testSearchConfiguration) {
                limit = 5
                sort { scoreField.desc() }
            }

        assertThat(query.limit).isEqualTo(5)
        assertThat(query.sort)
            .isEqualTo(
                Sort(
                    listOf(
                        SortField(scoreField, SortDirection.DESC),
                        SortField(nameField, SortDirection.ASC),
                    ),
                ),
            )
    }

    @Test
    fun `multiple asc desc calls inside one sort block all register in order`() {
        val query =
            search {
                sort {
                    displayName.asc()
                    id.desc()
                }
            }

        assertThat(query.sort)
            .isEqualTo(
                Sort(
                    listOf(
                        SortField(displayName, SortDirection.ASC),
                        SortField(id, SortDirection.DESC),
                    ),
                ),
            )
    }

    @Test
    fun `filter is null when never called`() {
        val query = search { sort { id.asc() } }

        assertThat(query.filter).isEqualTo(null)
    }

    @Test
    fun `after(vararg CursorElement) computes criteriaHash from this query's own sort and filter`() {
        val query =
            search {
                sort { displayName.asc() }

                filter { owner eq "Alain" }

                after(CursorElement.StringValue("last-value"))
            }

        val expectedHash = CriteriaHash.of(query.sort, query.filter)
        assertThat(query.cursor)
            .isEqualTo(
                CursorState(
                    listOf(CursorElement.StringValue("last-value")),
                    expectedHash,
                    criteria = CursorCriteria.of(query.sort, query.filter),
                ),
            )
    }

    @Test
    fun `after(vararg CursorElement) hash is unaffected by call order relative to sort and filter`() {
        val query =
            search {
                after(CursorElement.StringValue("last-value"))
                sort { displayName.asc() }

                filter { owner eq "Alain" }
            }

        val expectedHash = CriteriaHash.of(query.sort, query.filter)
        assertThat(query.cursor?.hash).isEqualTo(expectedHash)
    }

    @Test
    fun `before(vararg CursorElement) builds a cursor for the previous page`() {
        val query =
            search {
                sort { displayName.asc() }

                filter { owner eq "Alain" }

                before(CursorElement.StringValue("first-value"))
            }

        assertThat(query.cursor)
            .isEqualTo(
                CursorState(
                    listOf(CursorElement.StringValue("first-value")),
                    CriteriaHash.of(query.sort, query.filter),
                    CursorDirection.BEFORE,
                    criteria = CursorCriteria.of(query.sort, query.filter),
                ),
            )
    }

    @Test
    fun `the last of after and before wins`() {
        val query =
            search {
                sort { displayName.asc() }

                before(CursorElement.StringValue("a"))
                after(CursorElement.StringValue("b"))
            }

        assertThat(query.cursor?.direction).isEqualTo(CursorDirection.AFTER)
        assertThat(query.cursor?.values).containsExactly(CursorElement.StringValue("b"))
    }

    @Test
    fun `after(CursorState) uses the given state as is`() {
        val state = CursorState(listOf(CursorElement.StringValue("a")), "some-hash")

        val query =
            search {
                sort { displayName.asc() }

                after(state)
            }

        assertThat(query.cursor).isSameAs(state)
    }

    @Test
    fun `and combines 2 or more expressions into an AndExpression`() {
        val expr = and(owner eq "Alain", id eq "1")
        assertThat((expr as AndExpression).children.size).isEqualTo(2)
    }

    @Test
    fun `or combines 2 or more expressions into an OrExpression`() {
        val expr = or(owner eq "Alain", id eq "1")
        assertThat((expr as OrExpression).children.size).isEqualTo(2)
    }

    @Test
    fun `and or or of a single expression is that expression`() {
        val single = owner eq "Alain"

        assertThat(and(single)).isSameAs(single)
        assertThat(or(single)).isSameAs(single)
    }

    @Test
    fun `and or or without expressions fails`() {
        assertThatThrownBy { and() }
            .isInstanceOf(SearchException::class.java)
        assertThatThrownBy { or() }
            .isInstanceOf(SearchException::class.java)
    }

    @Test
    fun `filter can combine multiple conditions via and`() {
        val query =
            search {
                sort { id.asc() }

                filter { and(owner eq "Alain", id eq "1") }
            }

        assertThat(query.filter?.root?.let { it::class }).isEqualTo(AndExpression::class)
    }
}
