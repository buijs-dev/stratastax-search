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
package dev.buijs.stratastax.search.core.spi

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.SortField

/** Page size used when a request does not specify one. */
const val STANDARD_DEFAULT_LIMIT = 20

/** Upper bound on the page size a request may ask for. */
const val STANDARD_MAX_LIMIT = 100

/**
 * Describes what is searchable for one entity type.
 *
 * Instances are used as cache keys, so keep them long-lived (one per entity), not per request.
 */
interface SearchConfiguration {

    /** Page size used when a request does not specify one. */
    val defaultLimit: Int

    /** Upper bound on the page size a request may ask for. */
    val maxLimit: Int

    /** Sort applied when a request does not specify one. */
    val defaultSort: List<SortField>

    /** All fields known to the search engine. */
    val fields: Collection<EntityField>

    /** Unique field appended to every sort so cursor pagination is stable. */
    val tiebreakerField: EntityField

    /** Related-model groupings of [fields]. Empty by default. */
    val groups: Collection<FieldGroup>
        get() = emptyList()
}

/**
 * Plain data-class implementation of [SearchConfiguration] with the standard limits as defaults.
 */
data class StandardSearchConfiguration(
    override val defaultLimit: Int = STANDARD_DEFAULT_LIMIT,
    override val maxLimit: Int = STANDARD_MAX_LIMIT,
    override val defaultSort: List<SortField>,
    override val fields: Collection<EntityField>,
    override val tiebreakerField: EntityField,
    override val groups: Collection<FieldGroup> = emptyList(),
) : SearchConfiguration
