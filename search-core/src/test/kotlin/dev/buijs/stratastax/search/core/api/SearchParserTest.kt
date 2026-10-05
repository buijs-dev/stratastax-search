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
import dev.buijs.stratastax.search.core.spi.CursorElement.StringValue
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortField
import dev.buijs.stratastax.search.testdata.activeField
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.nameSortField
import dev.buijs.stratastax.search.testdata.playedAtField
import dev.buijs.stratastax.search.testdata.scoreField
import dev.buijs.stratastax.search.testdata.statusField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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

        @Test
        fun `should reject limit of zero`() {
            assertThatThrownBy { searchParser.parse(SearchRequest(limit = 0), configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Limit must be at least 1")
        }

        @Test
        fun `should reject negative limit`() {
            assertThatThrownBy { searchParser.parse(SearchRequest(limit = -1), configuration) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Limit must be at least 1")
        }

        @Test
        fun `should accept limit of one`() {
            assertThat(searchParser.parse(SearchRequest(limit = 1), configuration).limit)
                .isEqualTo(1)
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
    @DisplayName("Invalid cursors")
    inner class InvalidCursorTests {

        @Test
        fun `should throw InvalidCursorException for a malformed cursor`() {
            assertThatThrownBy {
                    searchParser.parse(SearchRequest(cursor = "not-a-cursor"), configuration)
                }
                .isInstanceOf(InvalidCursorException::class.java)
                .hasMessage("Invalid cursor")
        }

        @Test
        fun `should throw InvalidCursorException for a cursor with other criteria`() {
            val cursor = cursorFor(sort = "playedAt:DESC", filter = null)

            assertThatThrownBy {
                    searchParser.parse(
                        SearchRequest(
                            cursor = cursor,
                            criteria = SearchCriteria(sort = "playedAt:ASC"),
                        ),
                        configuration,
                    )
                }
                .isInstanceOf(InvalidCursorException::class.java)
                .hasMessageContaining("does not match")
        }
    }

    @Nested
    @DisplayName("Cursor expiry")
    inner class CursorExpiryTests {

        private val issuedAt = Instant.parse("2026-01-01T12:00:00Z")

        private val issuingCodec =
            HmacBase64CursorCodec(
                { "even-more-secret-key".toByteArray(Charsets.UTF_8) },
                clock = Clock.fixed(issuedAt, ZoneOffset.UTC),
            )

        private val cursor =
            issuingCodec.encode(
                CursorState(
                    listOf(StringValue("x")),
                    CriteriaHash.of(sortParser.parse(null, configuration), null),
                )
            )

        private fun parserAt(now: Instant, expiry: Duration? = null) =
            SearchParser(
                cursorCodec = issuingCodec,
                filterParser = filterParser,
                sortParser = sortParser,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                cursorExpiry = expiry ?: SearchParser.DEFAULT_CURSOR_EXPIRY,
            )

        @Test
        fun `default expiry is 24 hours`() {
            assertThat(SearchParser.DEFAULT_CURSOR_EXPIRY).isEqualTo(Duration.ofHours(24))
        }

        @Test
        fun `cursor is valid within the default expiry`() {
            val parser = parserAt(issuedAt.plus(Duration.ofHours(23)))

            assertThat(parser.parse(SearchRequest(cursor = cursor), configuration).cursor)
                .isNotNull()
        }

        @Test
        fun `cursor is still valid exactly at the expiry`() {
            val parser = parserAt(issuedAt.plus(Duration.ofHours(24)))

            assertThat(parser.parse(SearchRequest(cursor = cursor), configuration).cursor)
                .isNotNull()
        }

        @Test
        fun `cursor is rejected after the default expiry`() {
            val parser = parserAt(issuedAt.plus(Duration.ofHours(24)).plusSeconds(1))

            assertThatThrownBy { parser.parse(SearchRequest(cursor = cursor), configuration) }
                .isInstanceOf(InvalidCursorException::class.java)
                .hasMessage("Cursor has expired")
        }

        @Test
        fun `a custom expiry is honored`() {
            val parser = parserAt(issuedAt.plusSeconds(61), expiry = Duration.ofMinutes(1))

            assertThatThrownBy { parser.parse(SearchRequest(cursor = cursor), configuration) }
                .isInstanceOf(InvalidCursorException::class.java)
                .hasMessage("Cursor has expired")
        }
    }

    @Nested
    @DisplayName("Cursor versus criteria (README table)")
    inner class CursorCriteriaTests {

        private val sort = "playedAt:DESC"
        private val filter = "active==false"

        // A cursor issued for a request that used non-default sort and filter.
        private val cursor = cursorFor(sort, filter)

        private fun parse(
            cursor: String?,
            sort: String? = null,
            filter: String? = null,
            limit: Int? = null,
        ) =
            searchParser.parse(
                SearchRequest(
                    limit = limit,
                    cursor = cursor,
                    criteria =
                        if (sort == null && filter == null) null
                        else SearchCriteria(sort = sort, filter = filter),
                ),
                configuration,
            )

        @Test
        fun `absent cursor - first page`() {
            assertThat(parse(cursor = null, sort = sort, filter = filter).cursor).isNull()
        }

        @Test
        fun `present cursor with identical criteria is accepted`() {
            assertThat(parse(cursor, sort, filter).cursor).isNotNull()
        }

        @Test
        fun `present cursor with identical criteria in other casing is accepted`() {
            assertThat(parse(cursor, "PLAYEDAT:desc", "ACTIVE==false").cursor).isNotNull()
        }

        @Test
        fun `present cursor with all criteria omitted is accepted`() {
            // README: "present | omitted | ✔" - the client may send just limit and cursor.
            assertThat(parse(cursor).cursor).isNotNull()
        }

        @Test
        fun `present cursor with omitted criteria resumes with the original criteria`() {
            val query = parse(cursor)

            assertThat(query.sort.fields.first())
                .isEqualTo(SortField(playedAtField, SortDirection.DESC))
            assertThat(query.filter).isNotNull()
        }

        @Test
        fun `present cursor with only the filter is rejected as partial`() {
            assertThatThrownBy { parse(cursor, filter = filter) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `present cursor with only the sort is rejected as partial`() {
            assertThatThrownBy { parse(cursor, sort = sort) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `omitted sort equals the default sort when the cursor was issued for it`() {
            // Same effective criteria: nothing can be skipped or duplicated, so this is valid.
            val defaultSortCursor = cursorFor(sort = null, filter = filter)

            assertThat(parse(defaultSortCursor, filter = filter).cursor).isNotNull()
        }

        @Test
        fun `omitted sort is rejected when the cursor was issued for another sort`() {
            // README: "Error because sort values are missing".
            assertThatThrownBy { parse(cursor, filter = filter) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `empty criteria count as omitted`() {
            val query =
                searchParser.parse(
                    SearchRequest(cursor = cursor, criteria = SearchCriteria()),
                    configuration,
                )

            assertThat(query.filter).isNotNull()
        }

        @Test
        fun `omitted criteria resume a cursor that carries a filter-free criteria`() {
            val noFilter = cursorFor(sort = sort, filter = null)

            val query = parse(noFilter)

            assertThat(query.filter).isNull()
            assertThat(query.sort.fields.first().direction).isEqualTo(SortDirection.DESC)
        }

        @Test
        fun `omitted criteria on a cursor without criteria fall back to the default criteria`() {
            val bare = cursorFor(sort = null, filter = null, withCriteria = false)

            assertThat(parse(bare).cursor).isNotNull()
            assertThatThrownBy { parse(cursorFor(sort, filter, withCriteria = false)) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `criteria with special characters survive the cursor`() {
            val tricky = "name==\"a|b%c;d,e (f) \\\" 'g'\""
            val trickyCursor = cursorFor(sort, tricky)

            val query = parse(trickyCursor)

            assertThat(query.filter).isEqualTo(filterParser.parse(tricky, configuration))
        }

        @Test
        fun `a filter over the maximum length is rejected`() {
            val long = "name==" + "a".repeat(SearchParser.DEFAULT_MAX_FILTER_LENGTH)

            assertThatThrownBy { parse(cursor = null, filter = long) }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("Filter exceeds maximum length")
        }

        @Test
        fun `a cursor over the maximum length is rejected`() {
            assertThatThrownBy { parse("a".repeat(SearchParser.DEFAULT_MAX_CURSOR_LENGTH + 1)) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `a cursor for the longest allowed filter stays within the cursor limit`() {
            val longest = "name==\"" + "x".repeat(990) + "\""
            assertThat(longest.length).isLessThanOrEqualTo(SearchParser.DEFAULT_MAX_FILTER_LENGTH)

            val query = parse(cursor = null, filter = longest)
            val encoded =
                cursorCodec.encode(
                    CursorState(
                        listOf(StringValue("x")),
                        CriteriaHash.of(query.sort, query.filter),
                        criteria = CursorCriteria.of(query.sort, query.filter),
                    )
                )

            assertThat(encoded.length).isLessThanOrEqualTo(SearchParser.DEFAULT_MAX_CURSOR_LENGTH)
            assertThat(parse(encoded).filter).isEqualTo(query.filter)
        }

        @Test
        fun `present cursor with modified sort is rejected`() {
            assertThatThrownBy { parse(cursor, "playedAt:ASC", filter) }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `present cursor with modified filter is rejected`() {
            assertThatThrownBy { parse(cursor, sort, "active==true") }
                .isInstanceOf(InvalidCursorException::class.java)
        }

        @Test
        fun `limit is not part of the criteria and may change`() {
            val query = parse(cursor, sort, filter, limit = 5)

            assertThat(query.limit).isEqualTo(5)
            assertThat(query.cursor).isNotNull()
        }
    }

    private fun cursorFor(sort: String?, filter: String?, withCriteria: Boolean = true): String {
        val parsedSort = sortParser.parse(sort, configuration)
        val parsedFilter = filterParser.parse(filter, configuration)
        return cursorCodec.encode(
            CursorState(
                values = listOf(StringValue("x")),
                hash = CriteriaHash.of(parsedSort, parsedFilter),
                criteria = if (withCriteria) CursorCriteria.of(parsedSort, parsedFilter) else null,
            )
        )
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
            assertThat(result.cursor)
                .isEqualTo(cursorState.copy(issuedAt = result.cursor!!.issuedAt))

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
        hash = CriteriaHash.of(sort, filter),
    )
