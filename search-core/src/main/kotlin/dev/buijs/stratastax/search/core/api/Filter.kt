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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.search.core.app.validate

/**
 * Represents a search filter composed of a tree structure of expressions.
 *
 * @property root The main expression ([FilterExpression]) constituting the root of the filter tree.
 */
data class Filter(val root: FilterExpression)

/** Any node of a [Filter] tree. Sealed so backends can match on it exhaustively. */
sealed interface FilterExpression

/**
 * Expression that combines multiple child expressions using a logical **AND** operation.
 *
 * @property children The list of child expressions to evaluate.
 * @throws SearchException If [children] contains fewer than 2 expressions.
 */
data class AndExpression(val children: List<FilterExpression>) : FilterExpression {
    init {
        validate(children.size >= 2) { "AndExpression requires at least 2 child expressions." }
    }
}

/**
 * Expression that combines multiple child expressions using a logical **OR** operation.
 *
 * @property children The list of child expressions to evaluate.
 * @throws SearchException If [children] contains fewer than 2 expressions.
 */
data class OrExpression(val children: List<FilterExpression>) : FilterExpression {
    init {
        validate(children.size >= 2) { "OrExpression requires at least 2 child expressions." }
    }
}

/**
 * Expression for comparing a specific field against a single value using a comparison operator.
 *
 * @property field The [dev.buijs.stratastax.entity.core.api.EntityField] being evaluated.
 * @property operator The [ComparisonOperator] applied in the evaluation.
 * @property value The [FilterValue] to compare against.
 */
data class ComparisonExpression(
    val field: EntityField,
    val operator: ComparisonOperator,
    val value: FilterValue,
) : FilterExpression

/**
 * Expression for checking if a field value exists (or does not exist) within a list of values.
 *
 * @property field The [EntityField] being evaluated.
 * @property operator The [InOperator] applied (e.g., IN or OUT).
 * @property values The list of [FilterValue] instances to check against.
 * @throws SearchException If [values] is empty.
 */
data class InExpression(
    val field: EntityField,
    val operator: InOperator,
    val values: List<FilterValue>,
) : FilterExpression {
    init {
        validate(values.isNotEmpty()) { "In/Out expression requires a non-empty list of values." }
    }
}

/**
 * Expression for performing pattern/substring matching on a field.
 *
 * @property field The [EntityField] being matched.
 * @property pattern The pattern string to evaluate.
 */
data class LikeExpression(val field: EntityField, val pattern: String) : FilterExpression
