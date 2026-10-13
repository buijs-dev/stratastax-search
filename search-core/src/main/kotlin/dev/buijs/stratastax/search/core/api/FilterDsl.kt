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
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import dev.buijs.stratastax.search.core.app.validate
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

/** Field equals the raw [value], parsed according to the field type. */
fun EntityField.equalsValue(value: String): ComparisonExpression =
    comparison(ComparisonOperator.EQUALS, value)

/** Field does not equal the raw [value], parsed according to the field type. */
fun EntityField.notEqualsValue(value: String): ComparisonExpression =
    comparison(ComparisonOperator.NOT_EQUALS, value)

/** Field is `null`. */
fun EntityField.isNull(): ComparisonExpression =
    ComparisonExpression(this, ComparisonOperator.EQUALS, FilterValue.NullValue)

/** Field is not `null`. */
fun EntityField.isNotNull(): ComparisonExpression =
    ComparisonExpression(this, ComparisonOperator.NOT_EQUALS, FilterValue.NullValue)

/** Field is greater than the raw [value]. Only for number and date/time fields. */
fun EntityField.greaterThan(value: String): ComparisonExpression =
    comparison(ComparisonOperator.GREATER_THAN, value)

/** Field is greater than or equal to the raw [value]. Only for number and date/time fields. */
fun EntityField.greaterThanOrEqual(value: String): ComparisonExpression =
    comparison(ComparisonOperator.GREATER_THAN_OR_EQUAL, value)

/** Field is less than the raw [value]. Only for number and date/time fields. */
fun EntityField.lessThan(value: String): ComparisonExpression =
    comparison(ComparisonOperator.LESS_THAN, value)

/** Field is less than or equal to the raw [value]. Only for number and date/time fields. */
fun EntityField.lessThanOrEqual(value: String): ComparisonExpression =
    comparison(ComparisonOperator.LESS_THAN_OR_EQUAL, value)

/**
 * Field matches [pattern]. Only for string fields.
 *
 * @throws SearchException If the field is not a string.
 */
fun EntityField.like(pattern: String): LikeExpression {
    validate(ComparisonOperator.LIKE.allowed(type)) {
        "LIKE is not allowed for field '$name' of type $type"
    }

    return LikeExpression(this, pattern)
}

/**
 * Field matches [pattern], ignoring case. Only for string fields.
 *
 * @throws SearchException If the field is not a string.
 */
fun EntityField.ilike(pattern: String): LikeExpression {
    validate(ComparisonOperator.ILIKE.allowed(type)) {
        "ILIKE is not allowed for field '$name' of type $type"
    }

    return LikeExpression(this, pattern, ignoreCase = true)
}

/**
 * Field contains [value], ignoring case. Only for string fields. Wildcards in [value] (`%`, `_`)
 * are matched literally: they are escaped with a backslash.
 *
 * @throws SearchException If the field is not a string.
 */
fun EntityField.containsIgnoreCase(value: String): LikeExpression = ilike("%${escapeLike(value)}%")

private fun escapeLike(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** Field equals any of the raw [values]. Not for boolean, date/time or JSON fields. */
fun EntityField.inValues(vararg values: String): InExpression = inExpression(InOperator.IN, values)

/** Field equals none of the raw [values]. Not for boolean, date/time or JSON fields. */
fun EntityField.notInValues(vararg values: String): InExpression =
    inExpression(InOperator.OUT, values)

/**
 * Field equals the typed [value]; `null` matches a null field. Enums accept an [Enum] or its name.
 */
infix fun EntityField.eq(value: Any?): ComparisonExpression =
    typedComparison(ComparisonOperator.EQUALS, value)

/** Field does not equal the typed [value]; `null` matches a non-null field. */
infix fun EntityField.ne(value: Any?): ComparisonExpression =
    typedComparison(ComparisonOperator.NOT_EQUALS, value)

/** Field is greater than the typed [value]. Only for number and date/time fields. */
infix fun EntityField.gt(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.GREATER_THAN, value)

/** Field is greater than or equal to the typed [value]. Only for number and date/time fields. */
infix fun EntityField.ge(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.GREATER_THAN_OR_EQUAL, value)

/** Field is less than the typed [value]. Only for number and date/time fields. */
infix fun EntityField.lt(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.LESS_THAN, value)

/** Field is less than or equal to the typed [value]. Only for number and date/time fields. */
infix fun EntityField.le(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.LESS_THAN_OR_EQUAL, value)

/** Field equals any of the typed [values], which must not be empty. */
infix fun EntityField.isIn(values: Collection<Any>): InExpression =
    typedInExpression(InOperator.IN, values)

/** Field equals none of the typed [values], which must not be empty. */
infix fun EntityField.notIn(values: Collection<Any>): InExpression =
    typedInExpression(InOperator.OUT, values)

private fun EntityField.comparison(
    operator: ComparisonOperator,
    value: String,
): ComparisonExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }

    return ComparisonExpression(
        field = this,
        operator = operator,
        value = FilterValue.of(field = this, value = value, nullable = operator.nullable),
    )
}

private fun EntityField.inExpression(
    operator: InOperator,
    values: Array<out String>,
): InExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }

    validate(values.isNotEmpty()) {
        "$operator on field '$name' requires at least one value"
    }

    return InExpression(
        field = this,
        operator = operator,
        values =
            values.map { field ->
                FilterValue.of(field = this, value = field, nullable = operator.nullable)
            },
    )
}

private fun EntityField.typedComparison(
    operator: ComparisonOperator,
    value: Any?,
): ComparisonExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }

    return ComparisonExpression(field = this, operator = operator, value = toFilterValue(value))
}

private fun EntityField.typedInExpression(
    operator: InOperator,
    values: Collection<Any>,
): InExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }

    validate(values.isNotEmpty()) {
        "$operator on field '$name' requires at least one value"
    }

    return InExpression(
        field = this,
        operator = operator,
        values = values.map { toFilterValue(it) },
    )
}

private fun EntityField.toFilterValue(value: Any?): FilterValue =
    if (value == null) {
        FilterValue.NullValue
    } else {
        when (type) {
            EntityFieldType.STRING -> {
                FilterValue.StringValue(requireType(value))
            }
            EntityFieldType.ENUM -> {
                val enumName = if (value is Enum<*>) value.name else value as? String
                validate(enumName != null && enumValues.contains(enumName)) {
                    "Invalid enum value '$value' for field '$name'. Allowed values: ${
                        enumValues.joinToString(", ")
                    }"
                }

                FilterValue.EnumValue(enumName!!)
            }
            EntityFieldType.UUID -> {
                FilterValue.UuidValue(requireType(value))
            }
            EntityFieldType.BOOLEAN -> {
                FilterValue.BooleanValue(requireType(value))
            }
            EntityFieldType.NUMBER -> {
                FilterValue.NumberValue(
                    when (value) {
                        is BigDecimal -> value
                        is Int -> value.toBigDecimal()
                        is Long -> value.toBigDecimal()
                        is Double -> value.toBigDecimal()
                        is Float -> value.toBigDecimal()
                        is Short -> value.toInt().toBigDecimal()
                        else -> throw typeMismatch(value)
                    },
                )
            }
            EntityFieldType.DATE,
            EntityFieldType.DATETIME,
            -> {
                FilterValue.DateTimeValue(
                    when (value) {
                        is Instant -> value
                        is OffsetDateTime -> value.toInstant()
                        else -> throw typeMismatch(value)
                    },
                )
            }
            // Raw JSON text passed as a String, to avoid needing a JSON encoder in this module.
            EntityFieldType.JSON -> {
                FilterValue.JsonValue(requireType(value))
            }
        }
    }

private inline fun <reified T> EntityField.requireType(value: Any): T =
    value as? T ?: throw typeMismatch(value)

private fun EntityField.typeMismatch(value: Any): SearchException =
    SearchException(
        "Field '$name' is $type, but got a value of type ${value::class.simpleName}",
    )
