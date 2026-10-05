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

import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.api.Sort
import dev.buijs.stratastax.search.core.app.SearchFieldCache
import dev.buijs.stratastax.search.core.app.validate
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortField

/**
 * Writes a resolved [Sort] as `field:DIRECTION,...` and reads it back.
 *
 * Reading deliberately skips the `sortable` check of the [OrderedMultivaluedSortParser]: a resolved
 * sort also holds the default sort and the tiebreaker, which an endpoint may keep
 * client-unsortable. The text is only ever read from a signed cursor that this library wrote
 * itself.
 */
internal object SortWriter {

    fun write(sort: Sort): String =
        sort.fields.joinToString(",") { "${it.field.name}:${it.direction}" }

    fun read(text: String, configuration: SearchConfiguration): Sort =
        Sort(
            text.split(",").map { part ->
                val (name, direction) = part.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
                validate(direction != null) { "Invalid sort in cursor: '$part'" }
                val field =
                    SearchFieldCache.find(name, configuration)
                        ?: throw SearchException("Unknown sort field '$name' in cursor")
                SortField(field, SortDirection.of(direction!!, name))
            }
        )
}
