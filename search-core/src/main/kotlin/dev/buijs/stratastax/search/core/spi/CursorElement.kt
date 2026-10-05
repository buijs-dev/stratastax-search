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

import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.app.validate
import java.math.BigDecimal
import java.time.Instant

/**
 * Represents a typed data component within a cursor payload used for pagination state.
 *
 * This abstraction enables type safety during cursor serialization and deserialization, ensuring
 * values can be safely transported across boundaries without losing their intended representation.
 */
sealed class CursorElement {

    /** Provides the unique type discriminator for this element. */
    abstract fun prefix(): String

    /** Converts the underlying value into its raw string format suitable for encoding. */
    abstract fun serialize(): String

    /**
     * Combines the type discriminator and serialized value into a single encoded element
     * representation.
     */
    fun encode(): String = "${prefix()}$TYPE_VALUE_DELIMITER${serialize()}"

    /** A cursor element wrapper for text-based values. */
    data class StringValue(val value: String) : CursorElement() {
        override fun prefix(): String = TYPE_STRING

        override fun serialize(): String =
            value.replace("%", "%25").replace(COMPONENT_DELIMITER, "%7C")

        companion object {
            /** Restores a [StringValue] from its serialized string representation. */
            fun of(value: String): StringValue =
                StringValue(value.replace("%7C", COMPONENT_DELIMITER).replace("%25", "%"))
        }
    }

    /** A cursor element wrapper for high-precision numeric values. */
    data class NumberValue(val value: BigDecimal) : CursorElement() {
        override fun prefix(): String = TYPE_NUMBER

        override fun serialize(): String = value.toPlainString()

        companion object {
            /** Creates a [NumberValue] from a serialized numeric string. */
            fun of(value: String): NumberValue = NumberValue(BigDecimal(value))

            /** Creates a [NumberValue] from an integer value. */
            fun of(value: Int): NumberValue = NumberValue(value.toBigDecimal())
        }
    }

    /** A cursor element wrapper for boolean flags. */
    data class BooleanValue(val value: Boolean) : CursorElement() {
        override fun prefix(): String = TYPE_BOOLEAN

        override fun serialize(): String = "$value"

        companion object {
            /** Restores a [BooleanValue] from its string representation. */
            fun of(value: String): BooleanValue = BooleanValue(value.toBooleanStrict())
        }
    }

    /** A cursor element wrapper for point-in-time timestamp values. */
    data class TimestampValue(val value: Instant) : CursorElement() {
        override fun prefix(): String = TYPE_TIMESTAMP

        override fun serialize(): String = "${value.epochSecond}$TIMESTAMP_DELIMITER${value.nano}"

        companion object {
            private const val TIMESTAMP_DELIMITER = ","

            /**
             * Restores a [TimestampValue] from its serialized timestamp representation.
             *
             * @throws dev.buijs.stratastax.search.core.api.SearchException If [value] does not
             *   contain the seconds/nanos delimiter.
             */
            fun of(value: String): TimestampValue {
                val index = value.indexOf(TIMESTAMP_DELIMITER)
                validate(index != -1) { "Invalid cursor element format: $value" }

                val seconds = value.substring(0, index).toLong()
                val nanos = value.substring(index + 1).toInt()
                return TimestampValue(Instant.ofEpochSecond(seconds, nanos.toLong()))
            }
        }
    }

    /** Represents an explicitly absent or null value in a cursor. */
    data object NullValue : CursorElement() {
        override fun prefix(): String = TYPE_NULL

        override fun serialize(): String = ""
    }

    /**
     * A cursor element wrapper for a list of values - e.g. the membership of a filterable
     * `List`/`Set`-typed field (a 1:N relation's ids, tags, ...). Serialized as a length-prefixed
     * ("netstring") concatenation of each item's own [encode]d form, so no escaping is needed at
     * any nesting level regardless of what characters an item's own serialized form contains -
     * unlike [COMPONENT_DELIMITER]-joining (used for the top-level cursor payload), which only
     * works because every other variant's [serialize] guarantees it never emits an unescaped
     * [COMPONENT_DELIMITER].
     *
     * Preserves [values] in the order given - a `Set`'s iteration order isn't guaranteed stable, so
     * callers who need deterministic round-tripping should `.sorted()`/`.toSortedSet()` before
     * producing this (there's no generically-correct natural ordering to impose here across
     * arbitrary element types).
     */
    data class ListValue(val values: List<CursorElement>) : CursorElement() {
        override fun prefix(): String = TYPE_LIST

        override fun serialize(): String =
            values.joinToString("") {
                val encoded = it.encode()
                "${encoded.length}$LIST_ITEM_LENGTH_DELIMITER$encoded"
            }

        companion object {
            private const val LIST_ITEM_LENGTH_DELIMITER = ":"

            /**
             * Restores a [ListValue] from its length-prefixed serialized representation.
             *
             * @throws dev.buijs.stratastax.search.core.api.SearchException If [value] is malformed
             *   (missing a length prefix, a non-numeric length, or an item shorter than its
             *   declared length).
             */
            fun of(value: String): ListValue {
                val items = mutableListOf<CursorElement>()
                var i = 0
                while (i < value.length) {
                    val colon = value.indexOf(LIST_ITEM_LENGTH_DELIMITER, i)
                    validate(colon != -1) { "Invalid list cursor element format: $value" }
                    val length =
                        value.substring(i, colon).toIntOrNull()
                            ?: throw SearchException(
                                "Invalid list cursor element length in: $value"
                            )
                    val itemStart = colon + 1
                    val itemEnd = itemStart + length
                    validate(itemEnd <= value.length) {
                        "Invalid list cursor element format: $value"
                    }
                    items += CursorElement.of(value.substring(itemStart, itemEnd))
                    i = itemEnd
                }
                return ListValue(items)
            }
        }
    }

    companion object {
        /** Delimiter separating individual top-level components in the overall cursor payload. */
        const val COMPONENT_DELIMITER = "|"

        /**
         * Char version of [COMPONENT_DELIMITER] for use in [CursorCodec], to avoid unnecessary
         * allocations
         */
        const val COMPONENT_DELIMITER_CHAR = '|'

        /** Delimiter separating the type prefix from the serialized value in an encoded element. */
        const val TYPE_VALUE_DELIMITER = ":"

        /** Type prefix for string elements. */
        const val TYPE_STRING = "S"

        /** Type prefix for numeric elements. */
        const val TYPE_NUMBER = "N"

        /** Type prefix for boolean elements. */
        const val TYPE_BOOLEAN = "B"

        /** Type prefix for timestamp elements. */
        const val TYPE_TIMESTAMP = "T"

        /** Type prefix for null elements. */
        const val TYPE_NULL = "Z"

        /** Type prefix for list elements. */
        const val TYPE_LIST = "L"

        /**
         * Parses an encoded typed string back into its specific [CursorElement] instance.
         *
         * @throws dev.buijs.stratastax.search.core.api.SearchException If [value] is malformed or
         *   its type discriminator is unrecognized.
         */
        fun of(value: String): CursorElement {
            val index = value.indexOf(TYPE_VALUE_DELIMITER)
            validate(index != -1) { "Invalid cursor element format: $value" }

            val raw = value.substring(index + 1)
            return when (val type = value.substring(0, index)) {
                TYPE_STRING -> StringValue.of(raw)
                TYPE_NUMBER -> NumberValue.of(raw)
                TYPE_BOOLEAN -> BooleanValue.of(raw)
                TYPE_TIMESTAMP -> TimestampValue.of(raw)
                TYPE_NULL -> NullValue
                TYPE_LIST -> ListValue.of(raw)
                else -> throw SearchException("Unknown cursor type discriminator: $type")
            }
        }
    }
}

/**
 * Converts an arbitrary raw value into the matching [CursorElement] variant - `null` ->
 * [CursorElement.NullValue], otherwise dispatched via [registry] on this value's own runtime type.
 * This is what lets a `cursorValue { }` block return a plain value instead of an explicit
 * `CursorElement.XxxValue(...)`, and is also what search-jooq's bound-property-based
 * auto-derivation uses under the hood.
 *
 * Throws if [registry] has no [CursorValueFactory] for the runtime type - e.g. a wrapped id value
 * class - deliberately: the caller should either register a [CursorValueFactory] for it, or convert
 * it themselves (`cursorValue { it.someId.toString() }`) rather than this function guessing via a
 * blind `.toString()`, since Kotlin value classes commonly don't override `toString()` usefully.
 */
fun Any?.toCursorElement(
    fieldName: String,
    registry: CursorValueFactoryRegistry = CursorValueFactoryRegistry.default,
): CursorElement =
    when (this) {
        null -> CursorElement.NullValue
        is Iterable<*> ->
            CursorElement.ListValue(this.map { it.toCursorElement(fieldName, registry) })
        else ->
            registry.convert(this, fieldName)
                ?: throw SearchException(
                    "field(\"$fieldName\") - can't convert a ${this::class.simpleName} " +
                        "into a cursor value automatically. Register a " +
                        "CursorValueFactory<${this::class.simpleName}> bean " +
                        "(quarkus-starter) or pass a CursorValueFactoryRegistry entry " +
                        "for it, or call .toString() yourself first if this is a " +
                        "wrapper type."
                )
    }
