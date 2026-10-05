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
import dev.buijs.stratastax.search.core.spi.CursorElement
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortField
import dev.buijs.stratastax.search.testdata.activeField
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.nameSortField
import dev.buijs.stratastax.search.testdata.playedAtField
import dev.buijs.stratastax.search.testdata.scoreField
import dev.buijs.stratastax.search.testdata.statusField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SearchParserTest {

    private val filterParser = RsqlFilterParser()
    private val sortParser = OrderedMultivaluedSortParser()
    private val cursorCodec =
        HmacBase64CursorCodec({ "even-more-secret-key".toByteArray(Charsets.UTF_8) })

    private val searchParser =
        SearchParser(
            cursorCodec = cursorCodec,
            filterParser = filterParser,
            sortParser = sortParser,
        )

    private val configuration = testSearchConfiguration

    @Nested
    @DisplayName("Limit Scenarios")
    inner class LimitTests {

        @Test
        fun `should parse explicitly provided limit within max bound`() {
            // Given - testSearchConfiguration.maxLimit is 30, so this must stay
            // under it
            val request = SearchRequest(limit = 25)

            // When
            val result = searchParser.parse(request, configuration)

            // Then
            assertThat(result.limit).isEqualTo(25)
        }

        @Test
        fun `should fallback to configuration default limit when limit is null`() {
            // Given - testSearchConfiguration.defaultLimit is 10 (maxLimit is a
            // separate, higher bound of 30)
            val request = SearchRequest(limit = null)

            // When
            val result = searchParser.parse(request, configuration)

            // Then
            assertThat(result.limit).isEqualTo(10)
        }

        @Test
        fun `should reject limit exceeding configuration max limit`() {
            // Given - testSearchConfiguration.maxLimit is 30
            val request = SearchRequest(limit = 9_999)

            // When
            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
        }
    }

    @Nested
    @DisplayName("Cursor Scenarios")
    inner class CursorTests {

        @Test
        fun `should handle request without cursor`() {
            // Given
            val request = SearchRequest(cursor = null)

            // When
            val result = searchParser.parse(request, configuration)

            // Then
            assertThat(result.cursor).isNull()
        }

        @Test
        fun `should decode and attach valid encoded cursor`() {
            val originalCursor = testCursorState(id = "usr_9981", offset = 150)
            val encodedCursor = cursorCodec.encode(originalCursor)

            val request = SearchRequest(cursor = encodedCursor)

            val result = searchParser.parse(request, configuration)

            assertThat(result.cursor).isNotNull()
            assertThat(result.cursor).isInstanceOf(CursorState::class.java)

            val parsedState = result.cursor as CursorState
            assertThat(parsedState.values.size).isEqualTo(2)
            assertThat(parsedState.values[0]).isEqualTo(CursorElement.StringValue("usr_9981"))
            assertThat(parsedState.values[1]).isEqualTo(CursorElement.NumberValue.of(150))
        }

        @Test
        fun `should throw SearchException when cursor string is malformed or invalid base64`() {
            val invalidCursor = "invalid_base64_payload_!!!"
            val request = SearchRequest(cursor = invalidCursor)

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Invalid cursor")
        }
    }

    @Nested
    @DisplayName("Sorting Scenarios")
    inner class SortTests {

        @Test
        fun `should parse valid multi-field sorting`() {
            val request = SearchRequest(criteria = SearchCriteria(sort = "playedAt:DESC,name:ASC"))

            val result = searchParser.parse(request, configuration)

            val sortResult = result.sort
            assertThat(sortResult.fields.size).isEqualTo(2)
            assertThat(sortResult.fields[0]).isEqualTo(SortField(playedAtField, SortDirection.DESC))
            assertThat(sortResult.fields[1]).isEqualTo(SortField(nameField, SortDirection.ASC))
        }

        @Test
        fun `should throw SortParseException on duplicate conflicting sort criteria`() {
            val request = SearchRequest(criteria = SearchCriteria(sort = "score:ASC,score:DESC"))

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Duplicate sort field 'score' in sort expression")
        }

        @Test
        fun `should throw SortParseException on invalid sort direction`() {
            val request = SearchRequest(criteria = SearchCriteria(sort = "name:INVALID_DIRECTION"))

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `should throw SortParseException when sorting on a non-sortable field`() {
            // internalNotesField is explicitly configured with sortable = false
            // in the testdata
            val request = SearchRequest(criteria = SearchCriteria(sort = "internalNotes:ASC"))

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Field 'internalNotes' is not sortable")
        }
    }

    @Nested
    @DisplayName("Filtering Scenarios")
    inner class FilterTests {

        @Test
        fun `should parse valid combined filters`() {
            val request =
                SearchRequest(criteria = SearchCriteria(filter = "status==ACTIVE;score=ge=18"))

            val result = searchParser.parse(request, configuration)

            val filterResult = result.filter as Filter
            val expression = filterResult.root as AndExpression
            assertThat(expression.children.size).isEqualTo(2)

            val statusCondition = expression.children[0] as ComparisonExpression
            assertThat(statusCondition.field).isEqualTo(statusField)
            assertThat(statusCondition.operator).isEqualTo(ComparisonOperator.EQUALS)

            val scoreCondition = expression.children[1] as ComparisonExpression
            assertThat(scoreCondition.field).isEqualTo(scoreField)
            assertThat(scoreCondition.operator).isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL)
        }

        @Test
        fun `should throw FilterParseException on malformed filter syntax`() {
            val request =
                SearchRequest(criteria = SearchCriteria(filter = "invalid_filter_without_operator"))

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
        }

        @Test
        fun `should throw FilterParseException when filtering on an unknown field`() {
            val request = SearchRequest(criteria = SearchCriteria(filter = "doesNotExist==ACTIVE"))

            assertThatThrownBy { searchParser.parse(request, configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Unknown filter field 'doesNotExist'")
        }
    }

    @Nested
    @DisplayName("End-to-End Combinations")
    inner class IntegrationTests {

        @Test
        fun `should successfully parse complete request with all parameters active`() {
            val cursorState =
                testCursorState(
                    id = "item_123",
                    offset = 50,
                    sort =
                        Sort(
                            listOf(
                                SortField(playedAtField, SortDirection.DESC),
                                SortField(
                                    testSearchConfiguration.tiebreakerField,
                                    SortDirection.ASC,
                                ),
                            )
                        ),
                    filter =
                        Filter(
                            ComparisonExpression(
                                field = activeField,
                                operator = ComparisonOperator.EQUALS,
                                value = FilterValue.BooleanValue(false),
                            )
                        ),
                )

            val encodedCursor = cursorCodec.encode(cursorState)

            val request =
                SearchRequest(
                    limit = 15,
                    cursor = encodedCursor,
                    criteria = SearchCriteria(sort = "playedAt:DESC", filter = "active==false"),
                )

            val result = searchParser.parse(request, configuration)

            assertThat(result.limit).isEqualTo(15)
            assertThat(result.cursor).isEqualTo(cursorState)

            val sortResult = result.sort

            // 2 fields: playedAt from request and name tiebreaker which is
            // default
            assertThat(sortResult.fields.size).isEqualTo(2)
            assertThat(sortResult.fields[0]).isEqualTo(SortField(playedAtField, SortDirection.DESC))
            assertThat(sortResult.fields[1]).isEqualTo(nameSortField)

            val filterResult = result.filter as Filter
            val condition = filterResult.root as ComparisonExpression
            assertThat(condition.field).isEqualTo(activeField)
            assertThat(condition.operator).isEqualTo(ComparisonOperator.EQUALS)
        }
    }
}

private fun testCursorState(
    id: String,
    offset: Int,
    sort: Sort = Sort(testSearchConfiguration.defaultSort),
    filter: Filter? = null,
) =
    CursorState(
        values = listOf(CursorElement.StringValue(id), CursorElement.NumberValue.of(offset)),
        criteriaHash = CriteriaHash.of(sort, filter),
    )
