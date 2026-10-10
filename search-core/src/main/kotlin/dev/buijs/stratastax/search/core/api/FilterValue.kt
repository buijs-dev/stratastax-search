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
import dev.buijs.stratastax.search.core.api.CorruptFilterValue.InvalidBoolean
import dev.buijs.stratastax.search.core.api.CorruptFilterValue.InvalidDateTime
import dev.buijs.stratastax.search.core.api.CorruptFilterValue.InvalidEnum
import dev.buijs.stratastax.search.core.api.CorruptFilterValue.InvalidNumber
import dev.buijs.stratastax.search.core.api.CorruptFilterValue.InvalidUuid
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/** Represents a successfully parsed typed filter value. */
sealed interface FilterValue {

    /**
     * Wrapper for string filter values.
     *
     * @property value The string value.
     */
    data class StringValue(
        val value: String,
    ) : FilterValue

    /**
     * Wrapper for numeric filter values represented as high-precision [BigDecimal].
     *
     * @property value The numeric value.
     */
    data class NumberValue(
        val value: BigDecimal,
    ) : FilterValue

    /**
     * Wrapper for boolean filter values.
     *
     * @property value The boolean value.
     */
    data class BooleanValue(
        val value: Boolean,
    ) : FilterValue

    /**
     * Wrapper for date-time filter values represented as UTC [Instant].
     *
     * @property value The date-time value.
     */
    data class DateTimeValue(
        val value: Instant,
    ) : FilterValue

    /**
     * Wrapper for enumeration filter values.
     *
     * @property value The validated enum constant string.
     */
    data class EnumValue(
        val value: String,
    ) : FilterValue

    /**
     * Wrapper for [UUID] filter values.
     *
     * @property value The validated UUID value.
     */
    data class UuidValue(
        val value: UUID,
    ) : FilterValue

    /**
     * Wrapper for JSON filter values: the raw, unvalidated token text. A malformed value simply
     * never matches. Kept distinct from [StringValue] because backends bind it differently.
     *
     * @property value The raw JSON text.
     */
    data class JsonValue(
        val value: String,
    ) : FilterValue

    /** Represents an explicit null filter value. */
    data object NullValue : FilterValue

    companion object {

        /**
         * Parses a raw string value into a typed [FilterValue] for the given
         * [dev.buijs.stratastax.entity.core.api.EntityField], throwing a [SearchException] if
         * parsing fails or nullability rules are violated.
         *
         * @param field The [dev.buijs.stratastax.entity.core.api.EntityField] context defining
         *   expected data type and enum constraints.
         * @param value The raw string value to parse.
         * @param nullable Whether `null` values are allowed for the current operator context
         *   (defaults to `true`).
         * @param quoted Whether [value] was quoted in the query; a quoted `"null"` is the text
         *   `null`, not the null keyword (defaults to `false`).
         * @return A valid, typed [FilterValue].
         * @throws SearchException If [value] is invalid for the field type or if `null` is used
         *   where unsupported.
         */
        fun of(
            field: EntityField,
            value: String,
            nullable: Boolean = true,
            quoted: Boolean = false,
        ): FilterValue =
            when {
                quoted || !value.equals("null", ignoreCase = true) -> {
                    when (val result = of(field, value)) {
                        is ValidFilterValue -> {
                            result.value
                        }
                        is InvalidBoolean -> {
                            throw SearchException(
                                "Invalid boolean value '$value' for field '${field.name}'. Expected 'true' or 'false'",
                            )
                        }
                        is InvalidDateTime -> {
                            throw SearchException(
                                "Invalid ISO-8601 UTC date-time '$value' for field '${field.name}'",
                            )
                        }
                        is InvalidEnum -> {
                            val allowed = field.enumValues.joinToString(", ")
                            throw SearchException(
                                "Invalid enum value '$value' for field '${field.name}'. " +
                                    "Allowed values: $allowed",
                            )
                        }
                        is InvalidNumber -> {
                            throw SearchException(
                                "Invalid number format '$value' for field '${field.name}'",
                            )
                        }

                        is InvalidUuid -> {
                            throw SearchException(
                                "Invalid UUID format '$value' for field '${field.name}'",
                            )
                        }
                    }
                }
                nullable -> {
                    NullValue
                }
                else -> {
                    throw SearchException(
                        "NULL is only supported with '==' or '!=' for field '${field.name}'",
                    )
                }
            }

        /**
         * Converts a raw string value into a [FilterValueParseResult] according to the
         * [EntityFieldType] of [field].
         *
         * @param field The [EntityField] providing the target data type and validation metadata.
         * @param value The raw string input to parse.
         * @return A [FilterValue] on success or a [CorruptFilterValue] variant on parse failure.
         */
        internal fun of(
            field: EntityField,
            value: String,
        ): FilterValueParseResult =
            when (field.type) {
                EntityFieldType.STRING -> {
                    ValidFilterValue(StringValue(value))
                }

                EntityFieldType.NUMBER -> {
                    runCatching { ValidFilterValue(NumberValue(BigDecimal(value))) }
                        .getOrElse { InvalidNumber(field, value) }
                }

                EntityFieldType.BOOLEAN -> {
                    when {
                        value.equals("true", ignoreCase = true) -> {
                            ValidFilterValue(BooleanValue(true))
                        }
                        value.equals("false", ignoreCase = true) -> {
                            ValidFilterValue(BooleanValue(false))
                        }
                        else -> {
                            InvalidBoolean(field, value)
                        }
                    }
                }

                EntityFieldType.DATE,
                EntityFieldType.DATETIME,
                -> {
                    runCatching { ValidFilterValue(DateTimeValue(Instant.parse(value))) }
                        .getOrElse { InvalidDateTime(field, value) }
                }

                EntityFieldType.UUID -> {
                    runCatching { ValidFilterValue(UuidValue(UUID.fromString(value))) }
                        .getOrElse { InvalidUuid(field, value) }
                }

                // Enum values are matched case-sensitively by design,
                // unlike field names and operators,
                // which the README specifies as case-insensitive.
                EntityFieldType.ENUM -> {
                    if (field.enumValues.contains(value)) {
                        ValidFilterValue(EnumValue(value))
                    } else {
                        InvalidEnum(field, value)
                    }
                }

                EntityFieldType.JSON -> {
                    ValidFilterValue(JsonValue(value))
                }
            }
    }
}

internal sealed interface FilterValueParseResult

internal data class ValidFilterValue(
    val value: FilterValue,
) : FilterValueParseResult

/**
 * Represents a failed filter value parsing attempt where the raw string value could not be
 * converted to the field's target type.
 */
internal sealed interface CorruptFilterValue : FilterValueParseResult {

    /** The [EntityField] for which the parsing attempt failed. */
    val field: EntityField

    /** The raw string value that failed to parse. */
    val value: String

    /** Represents an invalid numeric format. */
    data class InvalidNumber(
        override val field: EntityField,
        override val value: String,
    ) : CorruptFilterValue

    /** Represents an invalid boolean value (neither 'true' nor 'false', case-insensitive). */
    data class InvalidBoolean(
        override val field: EntityField,
        override val value: String,
    ) : CorruptFilterValue

    /** Represents an invalid date-time value that does not conform to ISO-8601 UTC format. */
    data class InvalidDateTime(
        override val field: EntityField,
        override val value: String,
    ) : CorruptFilterValue

    /**
     * Represents an enum string value that is not among the allowed values defined in the target
     * [field].
     */
    data class InvalidEnum(
        override val field: EntityField,
        override val value: String,
    ) : CorruptFilterValue

    /** Represents an invalid UUID value. */
    data class InvalidUuid(
        override val field: EntityField,
        override val value: String,
    ) : CorruptFilterValue
}
