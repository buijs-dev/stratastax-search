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

import dev.buijs.stratastax.entity.core.api.EntityFieldType

/** Sealed interface representing filter operators within the search engine. */
sealed interface FilterOperator {
    companion object {
        /** List of all available operator tokens, sorted by length in descending order. */
        val values: List<String> by lazy {
            buildList {
                    addAll(ComparisonOperator.entries.map { it.token })
                    addAll(InOperator.entries.map { it.token })
                }
                .sortedByDescending { it.length }
        }

        /** Lookup map mapping token strings to their corresponding [FilterOperator] instances. */
        val asMap: Map<String, FilterOperator> by lazy {
            buildMap {
                putAll(ComparisonOperator.entries.associateBy { it.token })
                putAll(InOperator.entries.associateBy { it.token })
            }
        }

        /**
         * Retrieves the [FilterOperator] matching the provided token string.
         *
         * @param value The operator token string.
         * @return The matching [FilterOperator], or `null` if the token is unrecognized.
         */
        fun of(value: String): FilterOperator? = asMap[value]
    }
}

/**
 * Operators used for single-value comparison expressions.
 *
 * @property token The string token representing the comparison operator.
 * @property nullable Indicates if comparison against `null` is allowed.
 */
enum class ComparisonOperator(internal val token: String, internal val nullable: Boolean) :
    FilterOperator {

    /** Greater than (`=gt=`). Restricted to Date/Time and numeric types. */
    GREATER_THAN("=gt=", nullable = false) {
        override fun allowed(type: EntityFieldType) = type.isDateTimeOrNumber()
    },

    /** Greater than or equal to (`=ge=`). Restricted to Date/Time and numeric types. */
    GREATER_THAN_OR_EQUAL("=ge=", nullable = false) {
        override fun allowed(type: EntityFieldType) = type.isDateTimeOrNumber()
    },

    /** Less than (`=lt=`). Restricted to Date/Time and numeric types. */
    LESS_THAN("=lt=", nullable = false) {
        override fun allowed(type: EntityFieldType) = type.isDateTimeOrNumber()
    },

    /** Less than or equal to (`=le=`). Restricted to Date/Time and numeric types. */
    LESS_THAN_OR_EQUAL("=le=", nullable = false) {
        override fun allowed(type: EntityFieldType) = type.isDateTimeOrNumber()
    },

    /** Pattern matching (`=like=`). Restricted to [EntityFieldType.STRING]. */
    LIKE("=like=", nullable = false) {
        override fun allowed(type: EntityFieldType) = type == EntityFieldType.STRING
    },

    /** Equals (`==`). */
    EQUALS("==", nullable = true),

    /** Not equals (`!=`). */
    NOT_EQUALS("!=", nullable = true);

    /**
     * Checks if this operator is allowed for the specified [EntityFieldType].
     *
     * @param type The data type of the search field.
     * @return `true` if the operator is supported for the given field type; otherwise, `false`.
     */
    internal open fun allowed(type: EntityFieldType): Boolean = true
}

/**
 * Operators used for set-membership expressions.
 *
 * @property token The string token representing the set operator.
 * @property nullable Indicates if set operations on `null` values are allowed.
 */
enum class InOperator(internal val token: String, internal val nullable: Boolean) : FilterOperator {

    /** Exclude set elements (`=out=`). Not allowed for Boolean, Date/Time, or JSON types. */
    OUT("=out=", nullable = false) {
        override fun allowed(type: EntityFieldType) = !type.isBooleanOrDateTimeOrJson()
    },

    /** Include set elements (`=in=`). Not allowed for Boolean, Date/Time, or JSON types. */
    IN("=in=", nullable = false) {
        override fun allowed(type: EntityFieldType) = !type.isBooleanOrDateTimeOrJson()
    };

    /**
     * Checks if this operator is allowed for the specified [EntityFieldType].
     *
     * @param type The data type of the search field.
     * @return `true` if the operator is supported for the given field type; otherwise, `false`.
     */
    internal abstract fun allowed(type: EntityFieldType): Boolean
}

private fun EntityFieldType.isDateTimeOrNumber() =
    this == EntityFieldType.NUMBER ||
        this == EntityFieldType.DATE ||
        this == EntityFieldType.DATETIME

// None of these support set-membership; JSON is restricted to whole-value equality (`==`/`!=`).
private fun EntityFieldType.isBooleanOrDateTimeOrJson() =
    this == EntityFieldType.BOOLEAN ||
        this == EntityFieldType.DATE ||
        this == EntityFieldType.DATETIME ||
        this == EntityFieldType.JSON
