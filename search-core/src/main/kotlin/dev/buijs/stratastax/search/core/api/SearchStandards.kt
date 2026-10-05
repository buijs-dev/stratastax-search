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
import dev.buijs.stratastax.search.core.spi.CursorCodec
import dev.buijs.stratastax.search.core.spi.CursorSigningKey
import dev.buijs.stratastax.search.core.spi.FilterParser
import dev.buijs.stratastax.search.core.spi.SortParser

/**
 * Creates the default RSQL-based [FilterParser].
 *
 * @param maxNestingDepth Maximum nesting depth of parenthesized sub-expressions, to protect against
 *   a StackOverflowError from adversarial input.
 */
fun standardFilterParser(
    maxNestingDepth: Int = RsqlFilterParser.DEFAULT_MAX_NESTING_DEPTH
): FilterParser = RsqlFilterParser(maxNestingDepth)

/**
 * Creates the default [SortParser], which parses comma-separated `field:DIRECTION` expressions and
 * always appends the configuration's tiebreaker field for deterministic ordering.
 */
fun standardSortParser(): SortParser = OrderedMultivaluedSortParser()

/**
 * Creates the default [CursorCodec]: an HMAC-signed, URL-safe base64-encoded cursor.
 *
 * @param signingKey Supplies the HMAC signing key. See [CursorSigningKey] for deployment guidance.
 * @param version Payload version tag embedded in every cursor. Bump it when the payload format
 *   changes, so old cursors are rejected instead of silently misparsed.
 */
fun standardCursorCodec(signingKey: CursorSigningKey, version: String = "1"): CursorCodec =
    HmacBase64CursorCodec(signingKey, version)
