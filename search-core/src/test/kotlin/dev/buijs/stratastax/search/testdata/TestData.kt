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
package dev.buijs.stratastax.search.testdata

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.entity.core.api.EntityFieldType.BOOLEAN
import dev.buijs.stratastax.entity.core.api.EntityFieldType.DATETIME
import dev.buijs.stratastax.entity.core.api.EntityFieldType.ENUM
import dev.buijs.stratastax.entity.core.api.EntityFieldType.NUMBER
import dev.buijs.stratastax.entity.core.api.EntityFieldType.STRING
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import dev.buijs.stratastax.search.core.spi.SortDirection
import dev.buijs.stratastax.search.core.spi.SortField

internal val playedAtField = EntityField("playedAt", DATETIME, filterable = true, sortable = true)

internal val statusField =
    EntityField(
        "status",
        ENUM,
        filterable = true,
        enumValues = setOf("ACTIVE", "PENDING", "COMPLETED"),
    )

internal val scoreField = EntityField("score", NUMBER, sortable = true, filterable = true)

internal val nameField = EntityField("name", STRING, sortable = true, filterable = true)

internal val activeField = EntityField("active", BOOLEAN, filterable = true)

internal val internalNotesField =
    EntityField("internalNotes", STRING, sortable = false, filterable = false)

internal val nameSortField = SortField(nameField, SortDirection.ASC)

internal val statusSortField = SortField(statusField, SortDirection.ASC)

internal val testSearchConfiguration =
    object : SearchConfiguration {
        override val defaultLimit = 10
        override val maxLimit = 30
        override val defaultSort = listOf(nameSortField, statusSortField)
        override val fields =
            listOf(
                playedAtField,
                statusField,
                scoreField,
                nameField,
                activeField,
                internalNotesField,
            )
        override val tiebreakerField = nameField
    }
