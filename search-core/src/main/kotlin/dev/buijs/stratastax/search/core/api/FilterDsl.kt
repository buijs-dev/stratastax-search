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

// == Equality (allowed for every SearchFieldType; nullable per
// ComparisonOperator.EQUALS/NOT_EQUALS) ==

fun EntityField.equalsValue(value: String): ComparisonExpression =
    comparison(ComparisonOperator.EQUALS, value)

fun EntityField.notEqualsValue(value: String): ComparisonExpression =
    comparison(ComparisonOperator.NOT_EQUALS, value)

/**
 * Explicit null check - doesn't rely on the "null" string literal FilterValue.of also recognizes.
 */
fun EntityField.isNull(): ComparisonExpression =
    ComparisonExpression(this, ComparisonOperator.EQUALS, FilterValue.NullValue)

fun EntityField.isNotNull(): ComparisonExpression =
    ComparisonExpression(this, ComparisonOperator.NOT_EQUALS, FilterValue.NullValue)

// == Ordering comparisons (NUMBER/DATE/DATETIME only - see
// ComparisonOperator.allowed) ==

fun EntityField.greaterThan(value: String): ComparisonExpression =
    comparison(ComparisonOperator.GREATER_THAN, value)

fun EntityField.greaterThanOrEqual(value: String): ComparisonExpression =
    comparison(ComparisonOperator.GREATER_THAN_OR_EQUAL, value)

fun EntityField.lessThan(value: String): ComparisonExpression =
    comparison(ComparisonOperator.LESS_THAN, value)

fun EntityField.lessThanOrEqual(value: String): ComparisonExpression =
    comparison(ComparisonOperator.LESS_THAN_OR_EQUAL, value)

// == Pattern matching (STRING only) ==

fun EntityField.like(pattern: String): LikeExpression {
    validate(ComparisonOperator.LIKE.allowed(type)) {
        "LIKE is not allowed for field '$name' of type $type"
    }
    return LikeExpression(this, pattern)
}

// == Set membership (not BOOLEAN/DATE/DATETIME - see InOperator.allowed) ==

fun EntityField.inValues(vararg values: String): InExpression = inExpression(InOperator.IN, values)

fun EntityField.notInValues(vararg values: String): InExpression =
    inExpression(InOperator.OUT, values)

// == Typed comparisons - well-typed Kotlin values directly (String, UUID,
// BigDecimal/Int/Long/Double/Float/Short, Boolean, Instant/OffsetDateTime,
// enum-by-name-or-Enum<*>), no string round-trip through FilterValue.of.
// Complements the string-based functions above rather than replacing them -
// those still make sense for a caller with a genuinely string-shaped value
// (e.g. straight from somewhere already stringified). `in`/`out` are Kotlin
// keywords, hence `isIn`/`notIn`. `eq`/`ne` accept a nullable value,
// `gt`/`ge`/`lt`/`le`/`isIn`/`notIn` don't - mirrors
// ComparisonOperator/InOperator's own `nullable` flag, so passing `null`
// where it's not allowed is a compile error here, not a runtime one. ==

infix fun EntityField.eq(value: Any?): ComparisonExpression =
    typedComparison(ComparisonOperator.EQUALS, value)

infix fun EntityField.ne(value: Any?): ComparisonExpression =
    typedComparison(ComparisonOperator.NOT_EQUALS, value)

infix fun EntityField.gt(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.GREATER_THAN, value)

infix fun EntityField.ge(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.GREATER_THAN_OR_EQUAL, value)

infix fun EntityField.lt(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.LESS_THAN, value)

infix fun EntityField.le(value: Any): ComparisonExpression =
    typedComparison(ComparisonOperator.LESS_THAN_OR_EQUAL, value)

infix fun EntityField.isIn(values: Collection<Any>): InExpression =
    typedInExpression(InOperator.IN, values)

infix fun EntityField.notIn(values: Collection<Any>): InExpression =
    typedInExpression(InOperator.OUT, values)

// == shared plumbing ==

private fun EntityField.comparison(
    operator: ComparisonOperator,
    value: String,
): ComparisonExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }
    return ComparisonExpression(
        this,
        operator,
        FilterValue.of(this, value, nullable = operator.nullable),
    )
}

private fun EntityField.inExpression(
    operator: InOperator,
    values: Array<out String>,
): InExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }
    validate(values.isNotEmpty()) { "$operator on field '$name' requires at least one value" }
    return InExpression(
        field = this,
        operator = operator,
        values = values.map { FilterValue.of(this, it, nullable = operator.nullable) },
    )
}

private fun EntityField.typedComparison(
    operator: ComparisonOperator,
    value: Any?,
): ComparisonExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }
    return ComparisonExpression(this, operator, toFilterValue(value))
}

private fun EntityField.typedInExpression(
    operator: InOperator,
    values: Collection<Any>,
): InExpression {
    validate(operator.allowed(type)) {
        "Operator '$operator' is not allowed for field '$name' of type $type"
    }
    validate(values.isNotEmpty()) { "$operator on field '$name' requires at least one value" }
    return InExpression(this, operator, values.map { toFilterValue(it) })
}

/**
 * Builds a [FilterValue] directly from an already-typed Kotlin value, dispatching on this field's
 * own [EntityFieldType] - no string round-trip ([FilterValue.of]'s job, for genuinely string-shaped
 * input). Only [Instant]/[OffsetDateTime] are accepted for DATE/DATETIME - a bare
 * `LocalDateTime`/`LocalDate` has no inherent zone, and guessing UTC for it risks a silently-wrong
 * result; the caller must resolve the zone itself and pass an unambiguous type.
 */
private fun EntityField.toFilterValue(value: Any?): FilterValue {
    if (value == null) return FilterValue.NullValue
    return when (type) {
        EntityFieldType.STRING -> FilterValue.StringValue(requireType(value))
        EntityFieldType.ENUM -> {
            val enumName = if (value is Enum<*>) value.name else value as? String
            validate(enumName != null && enumValues.contains(enumName)) {
                "Invalid enum value '$value' for field '$name'. Allowed values: ${
                    enumValues.joinToString(", ")
                }"
            }
            FilterValue.EnumValue(enumName!!)
        }
        EntityFieldType.UUID -> FilterValue.UuidValue(requireType(value))
        EntityFieldType.BOOLEAN -> FilterValue.BooleanValue(requireType(value))
        EntityFieldType.NUMBER ->
            FilterValue.NumberValue(
                when (value) {
                    is BigDecimal -> value
                    is Int -> value.toBigDecimal()
                    is Long -> value.toBigDecimal()
                    is Double -> value.toBigDecimal()
                    is Float -> value.toBigDecimal()
                    is Short -> value.toInt().toBigDecimal()
                    else -> typeMismatch(value)
                }
            )
        EntityFieldType.DATE,
        EntityFieldType.DATETIME ->
            FilterValue.DateTimeValue(
                when (value) {
                    is Instant -> value
                    is OffsetDateTime -> value.toInstant()
                    else -> typeMismatch(value)
                }
            )
        // The raw JSON text, passed as a String - not a Map<String, Any?>,
        // to avoid needing a JSON encoder in this module (search-api has no
        // Vert.x/Jackson dependency) - matches ENUM's own String-accepting
        // shape above.
        EntityFieldType.JSON -> FilterValue.JsonValue(requireType(value))
    }
}

private inline fun <reified T> EntityField.requireType(value: Any): T =
    value as? T ?: typeMismatch(value)

private fun EntityField.typeMismatch(value: Any): Nothing =
    throw SearchException(
        "Field '$name' is $type, but got a value of type ${value::class.simpleName}"
    )
