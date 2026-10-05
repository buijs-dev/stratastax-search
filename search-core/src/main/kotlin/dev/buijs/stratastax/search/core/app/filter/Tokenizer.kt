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

import dev.buijs.stratastax.search.core.api.SearchException

internal data class Token(val type: TokenType, val text: String)

internal enum class TokenType(val description: String) {
    FIELD("field name"),
    OPERATOR("operator"),
    VALUE("value"),
    LPAREN("'('"),
    RPAREN("')'"),
    AND("';' or 'and'"),
    OR("',' or 'or'"),
}

internal class Tokenizer(private val input: String) {

    fun tokenize(): List<Token> = TokenMapper(RawScanner(input).scan()).map()
}

private class TokenMapper(private val rawTokens: List<RawToken>) {

    private enum class Position {
        FIELD,
        VALUE,
        CONNECTOR,
    }

    fun map(): List<Token> {
        val result = mutableListOf<Token>()
        var position = Position.FIELD
        var inList = false

        var i = 0
        while (i < rawTokens.size) {
            val current = rawTokens[i]

            when (current.type) {
                RawType.SYMBOL -> {
                    when (current.text) {
                        "(" -> {
                            result.add(Token(TokenType.LPAREN, "("))
                            position = if (inList) Position.VALUE else Position.FIELD
                        }
                        ")" -> {
                            result.add(Token(TokenType.RPAREN, ")"))
                            position = Position.CONNECTOR
                            inList = false
                        }
                        ";" -> {
                            result.add(Token(TokenType.AND, ";"))
                            position = Position.FIELD
                        }
                        "," -> {
                            if (inList) {
                                // Comma in IN/OUT list separates values, no
                                // token emitted
                                validateNoTrailingComma(i)
                            } else {
                                result.add(Token(TokenType.OR, ","))
                                position = Position.FIELD
                            }
                        }
                    }
                }

                RawType.OPERATOR -> {
                    result.add(Token(TokenType.OPERATOR, current.text))
                    position = Position.VALUE
                    if (
                        current.text.equals("=in=", ignoreCase = true) ||
                            current.text.equals("=out=", ignoreCase = true)
                    ) {
                        inList = true
                    }
                }

                RawType.QUOTED_STRING -> {
                    // Always a literal value - a quoted string can never be
                    // mistaken for a field name or an and/or keyword.
                    result.add(Token(TokenType.VALUE, current.text))
                    if (!inList) position = Position.CONNECTOR
                }

                RawType.WORD -> {
                    val text = current.text
                    when {
                        inList -> result.add(Token(TokenType.VALUE, text))

                        position == Position.CONNECTOR && text.equals("and", ignoreCase = true) -> {
                            result.add(Token(TokenType.AND, text))
                            position = Position.FIELD
                        }
                        position == Position.CONNECTOR && text.equals("or", ignoreCase = true) -> {
                            result.add(Token(TokenType.OR, text))
                            position = Position.FIELD
                        }
                        position == Position.FIELD -> {
                            result.add(Token(TokenType.FIELD, text))
                            position = Position.VALUE
                        }
                        position == Position.VALUE -> {
                            result.add(Token(TokenType.VALUE, text))
                            position = Position.CONNECTOR
                        }
                        else -> {
                            result.add(Token(TokenType.FIELD, text))
                            position = Position.VALUE
                        }
                    }
                }
            }
            i++
        }
        return result
    }

    private fun validateNoTrailingComma(currentIndex: Int) {
        val next = rawTokens.getOrNull(currentIndex + 1)
        if (next?.type == RawType.SYMBOL && next.text == ")") {
            throw SearchException(
                "Trailing comma is not allowed before ')' at position ${next.position}"
            )
        }
    }
}
