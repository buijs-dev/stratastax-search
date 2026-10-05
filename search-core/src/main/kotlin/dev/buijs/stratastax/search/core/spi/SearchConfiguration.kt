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

const val STANDARD_DEFAULT_LIMIT = 20
const val STANDARD_MAX_LIMIT = 100

interface SearchConfiguration {
    val defaultLimit: Int
    val maxLimit: Int
    val defaultSort: List<SortField>
    val fields: Collection<EntityField>
    val tiebreakerField: EntityField

    /**
     * Default-implemented so existing implementers (including anything gradle-plugin generates)
     * keep compiling without needing to override it.
     */
    val groups: Collection<FieldGroup>
        get() = emptyList()
}

data class StandardSearchConfiguration(
    override val defaultLimit: Int = STANDARD_DEFAULT_LIMIT,
    override val maxLimit: Int = STANDARD_MAX_LIMIT,
    override val defaultSort: List<SortField>,
    override val fields: Collection<EntityField>,
    override val tiebreakerField: EntityField,
    override val groups: Collection<FieldGroup> = emptyList(),
) : SearchConfiguration
