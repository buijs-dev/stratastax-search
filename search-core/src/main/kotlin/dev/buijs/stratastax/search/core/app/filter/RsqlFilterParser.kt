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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.search.core.api.AndExpression
import dev.buijs.stratastax.search.core.api.ComparisonExpression
import dev.buijs.stratastax.search.core.api.ComparisonOperator
import dev.buijs.stratastax.search.core.api.Filter
import dev.buijs.stratastax.search.core.api.FilterExpression
import dev.buijs.stratastax.search.core.api.FilterOperator
import dev.buijs.stratastax.search.core.api.FilterValue.Companion.of
import dev.buijs.stratastax.search.core.api.InExpression
import dev.buijs.stratastax.search.core.api.InOperator
import dev.buijs.stratastax.search.core.api.LikeExpression
import dev.buijs.stratastax.search.core.api.OrExpression
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.app.SearchFieldCache
import dev.buijs.stratastax.search.core.spi.FilterParser
import dev.buijs.stratastax.search.core.spi.SearchConfiguration

/**
 * An RSQL-compliant implementation of [FilterParser] that transforms RSQL query strings into
 * structured AST [Filter] representations.
 *
 * @property maxNestingDepth Maximum allowed nesting depth for parenthesized sub-expressions to
 *   prevent StackOverflowError (DoS).
 */
internal class RsqlFilterParser(
    private val maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH,
) : FilterParser {

    /**
     * Parses an RSQL filter query string into a structured [Filter] AST tree.
     *
     * @param value The raw RSQL string to parse.
     * @param configuration The active [SearchConfiguration] used to resolve field mapping and
     *   validate filterability.
     * @return The resulting [Filter] object, or `null` if [value] is null, blank, or tokenizes into
     *   an empty list.
     * @throws SearchException If tokenization, depth limits, or expression parsing fails.
     */
    override fun parse(
        value: String?,
        configuration: SearchConfiguration,
    ): Filter? {
        if (value.isNullOrBlank()) {
            return null
        }

        val tokens = Tokenizer(value).tokenize()
        val fieldMap = SearchFieldCache.fieldMapFor(configuration)
        return Filter(root = ExpressionParser(tokens, fieldMap, maxNestingDepth).parseOrThrow())
    }

    companion object {
        /** Default limit for maximum allowed nesting depth of sub-expressions. */
        const val DEFAULT_MAX_NESTING_DEPTH = 100
    }
}

private class ExpressionParser(
    private val tokens: List<Token>,
    private val fieldMap: Map<String, EntityField>,
    private val maxNestingDepth: Int,
) {

    private var cursor = 0
    private var currentNestingDepth = 0

    /** Parses the complete token sequence into a root [FilterExpression]. */
    fun parseOrThrow(): FilterExpression =
        parseOr {
            // Verify no unconsumed trailing tokens remain.
            ensureEndOfInput()
        }

    private fun parseOr(finalize: () -> Unit): FilterExpression =
        parse(TokenType.OR) { parseAnd() }
            .selectFirstOrWrapList { OrExpression(it) }
            .also { finalize() }

    private fun parseAnd(): FilterExpression =
        parse(TokenType.AND) { parsePrimary() }.selectFirstOrWrapList { AndExpression(it) }

    private inline fun parse(
        token: TokenType,
        action: () -> FilterExpression,
    ): List<FilterExpression> =
        buildList {
            do {
                add(action())
            } while (match(token))
        }

    private fun List<FilterExpression>.selectFirstOrWrapList(
        action: (List<FilterExpression>) -> FilterExpression,
    ): FilterExpression =
        if (size == 1) {
            this[0]
        } else {
            action(this)
        }

    private fun parsePrimary(): FilterExpression =
        when {
            match(TokenType.LPAREN) -> {
                currentNestingDepth++
                if (currentNestingDepth > maxNestingDepth) {
                    throw exceededMaxFilterDepth(maxNestingDepth)
                }

                parseOr {
                    // Consume matching closing ')' and decrease depth
                    consume(TokenType.RPAREN)
                    currentNestingDepth--
                }
            }

            else -> {
                parseOperator(parseField())
            }
        }

    private fun parseField(): EntityField {
        val token = consume(TokenType.FIELD)
        val tokenText = token.text
        val field = fieldMap[tokenText.lowercase()]
        return when {
            field == null -> throw unknownField(tokenText)
            field.filterable -> field
            else -> throw nonFilterableField(field)
        }
    }

    private fun parseOperator(field: EntityField): FilterExpression {
        val token = consume(TokenType.OPERATOR)
        val tokenText = token.text
        // The tokenizer only emits operators from FilterOperator.values, so the lookup cannot miss.
        return when (val operator = FilterOperator.asMap.getValue(tokenText)) {
            is ComparisonOperator -> {
                if (operator.allowed(field.type)) {
                    parseComparison(field, operator)
                } else {
                    throw invalidOperatorForField(operator, field)
                }
            }

            is InOperator -> {
                if (operator.allowed(field.type)) {
                    parseIn(field, operator)
                } else {
                    throw invalidOperatorForField(operator, field)
                }
            }
        }
    }

    private fun parseComparison(
        field: EntityField,
        operator: ComparisonOperator,
    ): FilterExpression {
        val value = consume(TokenType.VALUE)
        return when (operator) {
            ComparisonOperator.LIKE -> {
                LikeExpression(field, value.text)
            }

            ComparisonOperator.ILIKE -> {
                LikeExpression(field, value.text, ignoreCase = true)
            }

            else -> {
                val filter = of(field, value.text, operator.nullable, value.quoted)
                ComparisonExpression(field, operator, filter)
            }
        }
    }

    private fun parseIn(
        field: EntityField,
        operator: InOperator,
    ): InExpression {
        consume(TokenType.LPAREN)
        val values =
            buildList {
                while (peek()?.type == TokenType.VALUE) {
                    val value = consume(TokenType.VALUE)
                    val filter = of(field, value.text, operator.nullable, value.quoted)
                    add(filter)
                }
            }.ifEmpty { throw emptyValueList(field) }

        consume(TokenType.RPAREN)
        return InExpression(field, operator, values)
    }

    private fun match(type: TokenType): Boolean =
        if (peek()?.type == type) {
            cursor++
            true
        } else {
            false
        }

    private fun consume(type: TokenType): Token {
        val token = peek()
        if (token?.type == type) {
            cursor++
            return token
        }

        throw expectedToken(type, token)
    }

    private fun ensureEndOfInput() {
        peek()?.let { throw unexpectedToken(it) }
    }

    private fun peek(): Token? =
        if (cursor < tokens.size) {
            tokens[cursor]
        } else {
            null
        }
}

private fun exceededMaxFilterDepth(maxNestingDepth: Int): SearchException =
    SearchException("Exceeded maximum filter nesting depth of $maxNestingDepth.")

private fun expectedToken(
    expected: TokenType,
    actual: Token?,
): SearchException =
    SearchException(
        "Expected ${expected.description}, but got ${
            actual?.let { "'${it.text}'" } ?: "end of input"
        }",
    )

private fun unexpectedToken(token: Token): SearchException =
    SearchException("Unexpected token '${token.text}' at end of query")

private fun unknownField(field: String): SearchException =
    SearchException("Unknown filter field '$field'")

private fun nonFilterableField(field: EntityField): SearchException =
    SearchException("Field '${field.name}' is not filterable")

private fun invalidOperatorForField(
    operator: ComparisonOperator,
    field: EntityField,
): SearchException = invalidOperatorForField(operator.token, field)

private fun invalidOperatorForField(
    operator: InOperator,
    field: EntityField,
): SearchException = invalidOperatorForField(operator.token, field)

private fun invalidOperatorForField(
    token: String,
    field: EntityField,
): SearchException =
    SearchException(
        "Operator '$token' is not supported for " +
            "data type ${field.type} on field '${field.name}'",
    )

private fun emptyValueList(field: EntityField): SearchException =
    SearchException("IN/OUT list for field '${field.name}' cannot be empty")
