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

import dev.buijs.stratastax.search.core.api.FilterOperator
import dev.buijs.stratastax.search.core.api.SearchException

internal enum class RawType {
    SYMBOL, // ( ) ; ,
    OPERATOR, // == != =gt= =ge= =lt= =le= =in= =out= =like=
    WORD, // unquoted field or value
    QUOTED_STRING, // "..." of '...'
}

internal data class RawToken(val type: RawType, val text: String, val position: Int)

internal class RawScanner(private val input: String) {
    private var pos = 0
    private val len = input.length
    private var justSawOperator = false
    private var inList = false

    fun scan(): List<RawToken> = buildList {
        while (pos < len) {
            skipWhitespace()
            if (pos >= len) break

            val start = pos
            val lenient = justSawOperator || inList

            val token =
                when (val ch = input[pos]) {
                    in "();," -> {
                        pos++
                        RawToken(RawType.SYMBOL, ch.toString(), start)
                    }
                    '"',
                    '\'' -> RawToken(RawType.QUOTED_STRING, readQuoted(ch), start)
                    '=',
                    '!' -> {
                        if (lenient) {
                            RawToken(RawType.WORD, readWord(lenient = true), start)
                        } else {
                            RawToken(RawType.OPERATOR, readOperator(), start)
                        }
                    }
                    else -> RawToken(RawType.WORD, readWord(lenient = lenient), start)
                }

            when (token.type) {
                RawType.OPERATOR -> {
                    justSawOperator = true
                    if (
                        token.text.equals("=in=", ignoreCase = true) ||
                            token.text.equals("=out=", ignoreCase = true)
                    ) {
                        inList = true
                    }
                }
                RawType.SYMBOL -> {
                    if (token.text == ")") inList = false
                    justSawOperator = false
                }
                else -> justSawOperator = false
            }

            add(token)
        }
    }

    private fun readOperator(): String {
        return FilterOperator.values
            .firstOrNull { op -> input.startsWith(op, pos, ignoreCase = true) }
            ?.also { op -> pos += op.length }
            ?: throw SearchException("Unknown or malformed operator starting at position $pos")
    }

    private fun readWord(lenient: Boolean): String {
        val stopChars = if (lenient) ";,()\"'" else "=!;,()\"'"
        val start = pos
        while (pos < len && !input[pos].isWhitespace() && input[pos] !in stopChars) {
            pos++
        }
        return input.substring(start, pos)
    }

    private fun readQuoted(quote: Char): String {
        pos++ // skip start quote
        val sb = StringBuilder()
        var escaped = false
        while (pos < len) {
            val ch = input[pos++]
            if (escaped) {
                sb.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == quote) {
                return sb.toString()
            } else {
                sb.append(ch)
            }
        }
        throw SearchException("Unterminated string starting with $quote")
    }

    private fun skipWhitespace() {
        while (pos < len && input[pos].isWhitespace()) pos++
    }
}
