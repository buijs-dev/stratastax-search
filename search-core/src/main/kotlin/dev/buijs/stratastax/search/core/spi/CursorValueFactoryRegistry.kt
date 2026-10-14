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

import dev.buijs.stratastax.search.core.app.validate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Immutable, thread-safe lookup table from a raw value's runtime [KClass] to the
 * [CursorValueFactory] that converts it. Used by [Any?.toCursorElement].
 */
class CursorValueFactoryRegistry
    private constructor(
        private val byType: Map<KClass<out Any>, CursorValueFactory<out Any>>,
    ) {

        /**
         * Converts [value] via the factory registered for its exact runtime type, falling back to the
         * [Enum]/[Map] sentinel entries (see [CursorValueFactory]).
         *
         * @return The converted element, or `null` (never throws) if no factory matches.
         */
        @Suppress("UNCHECKED_CAST")
        fun convert(
            value: Any,
            fieldName: String,
        ): CursorElement? {
            val factory =
                byType[value::class]
                    ?: (value as? Enum<*>)?.let { byType[Enum::class] }
                    ?: (value as? Map<*, *>)?.let { byType[Map::class] }
                    ?: return null
            return (factory as CursorValueFactory<Any>).convert(value, fieldName)
        }

        /** Builds a new, independent [CursorValueFactoryRegistry]. */
        class Builder internal constructor() {

            private val entries = LinkedHashMap<KClass<out Any>, CursorValueFactory<out Any>>()

            /**
             * Registers [factory] under its own [CursorValueFactory.type].
             *
             * @throws dev.buijs.stratastax.search.core.api.SearchException If a factory is already
             *   registered for that type. Duplicates are a configuration mistake, never "last one
             *   wins".
             */
            fun <T : Any> register(factory: CursorValueFactory<T>): Builder =
                apply {
                    validate(!entries.containsKey(factory.type)) {
                        "A CursorValueFactory is already registered for " +
                            "${factory.type.simpleName} - registering a second one " +
                            "for the same type is not supported."
                    }

                    entries[factory.type] = factory
                }

            /** Builds the immutable registry from the factories registered so far. */
            fun build(): CursorValueFactoryRegistry = CursorValueFactoryRegistry(entries.toMap())
        }

        companion object {

            /** Starts a new, empty [Builder]. */
            fun builder(): Builder = Builder()

            /** Registry with a factory for every standard type listed below. */
            val default: CursorValueFactoryRegistry by lazy {
                builder()
                    .register(StringCursorValueFactory)
                    .register(UuidCursorValueFactory)
                    .register(EnumCursorValueFactory)
                    .register(BooleanCursorValueFactory)
                    .register(BigDecimalCursorValueFactory)
                    .register(IntCursorValueFactory)
                    .register(LongCursorValueFactory)
                    .register(DoubleCursorValueFactory)
                    .register(FloatCursorValueFactory)
                    .register(ShortCursorValueFactory)
                    .register(InstantCursorValueFactory)
                    .register(OffsetDateTimeCursorValueFactory)
                    .register(LocalDateTimeCursorValueFactory)
                    .register(LocalDateCursorValueFactory)
                    .register(TimestampCursorValueFactory)
                    .build()
            }
        }
    }

// The default [CursorValueFactory] implementations, one per standard type. Public so other
// modules can reuse the same instances instead of re-implementing the conversions.

/** Converts a [String] to a [CursorElement.StringValue]. */
object StringCursorValueFactory : CursorValueFactory<String> {

    override val type: KClass<String> = String::class

    override fun convert(
        value: String,
        fieldName: String,
    ): CursorElement = CursorElement.StringValue(value)
}

/** Converts a [UUID] to a [CursorElement.StringValue] of its canonical text form. */
object UuidCursorValueFactory : CursorValueFactory<UUID> {

    override val type: KClass<UUID> = UUID::class

    override fun convert(
        value: UUID,
        fieldName: String,
    ): CursorElement = CursorElement.StringValue(value.toString())
}

/**
 * Converts any [Enum] to a [CursorElement.StringValue] of its [Enum.name]. Registered under the
 * [Enum] sentinel type.
 */
object EnumCursorValueFactory : CursorValueFactory<Enum<*>> {

    override val type: KClass<Enum<*>> = Enum::class

    override fun convert(
        value: Enum<*>,
        fieldName: String,
    ): CursorElement = CursorElement.StringValue(value.name)
}

/** Converts a [Boolean] to a [CursorElement.BooleanValue]. */
object BooleanCursorValueFactory : CursorValueFactory<Boolean> {

    override val type: KClass<Boolean> = Boolean::class

    override fun convert(
        value: Boolean,
        fieldName: String,
    ): CursorElement = CursorElement.BooleanValue(value)
}

/** Converts a [BigDecimal] to a [CursorElement.NumberValue]. */
object BigDecimalCursorValueFactory : CursorValueFactory<BigDecimal> {

    override val type: KClass<BigDecimal> = BigDecimal::class

    override fun convert(
        value: BigDecimal,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value)
}

/** Converts an [Int] to a [CursorElement.NumberValue]. */
object IntCursorValueFactory : CursorValueFactory<Int> {

    override val type: KClass<Int> = Int::class

    override fun convert(
        value: Int,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value.toBigDecimal())
}

/** Converts a [Long] to a [CursorElement.NumberValue]. */
object LongCursorValueFactory : CursorValueFactory<Long> {

    override val type: KClass<Long> = Long::class

    override fun convert(
        value: Long,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value.toBigDecimal())
}

/** Converts a [Double] to a [CursorElement.NumberValue]. */
object DoubleCursorValueFactory : CursorValueFactory<Double> {

    override val type: KClass<Double> = Double::class

    override fun convert(
        value: Double,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value.toBigDecimal())
}

/**
 * Converts a [Float] to a [CursorElement.NumberValue] via [Double], so the decimal expansion of the
 * float is kept as-is.
 */
object FloatCursorValueFactory : CursorValueFactory<Float> {

    override val type: KClass<Float> = Float::class

    override fun convert(
        value: Float,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value.toDouble().toBigDecimal())
}

/** Converts a [Short] to a [CursorElement.NumberValue]. */
object ShortCursorValueFactory : CursorValueFactory<Short> {

    override val type: KClass<Short> = Short::class

    override fun convert(
        value: Short,
        fieldName: String,
    ): CursorElement = CursorElement.NumberValue(value.toInt().toBigDecimal())
}

/** Converts an [Instant] to a [CursorElement.TimestampValue]. */
object InstantCursorValueFactory : CursorValueFactory<Instant> {

    override val type: KClass<Instant> = Instant::class

    override fun convert(
        value: Instant,
        fieldName: String,
    ): CursorElement = CursorElement.TimestampValue(value)
}

/** Converts an [OffsetDateTime] to a [CursorElement.TimestampValue] at the same instant. */
object OffsetDateTimeCursorValueFactory : CursorValueFactory<OffsetDateTime> {

    override val type: KClass<OffsetDateTime> = OffsetDateTime::class

    override fun convert(
        value: OffsetDateTime,
        fieldName: String,
    ): CursorElement = CursorElement.TimestampValue(value.toInstant())
}

/** Converts a [LocalDateTime] to a [CursorElement.TimestampValue], interpreting it as UTC. */
object LocalDateTimeCursorValueFactory : CursorValueFactory<LocalDateTime> {

    override val type: KClass<LocalDateTime> = LocalDateTime::class

    override fun convert(
        value: LocalDateTime,
        fieldName: String,
    ): CursorElement = CursorElement.TimestampValue(value.toInstant(ZoneOffset.UTC))
}

/** Converts a [LocalDate] to a [CursorElement.TimestampValue] at the start of that day in UTC. */
object LocalDateCursorValueFactory : CursorValueFactory<LocalDate> {

    override val type: KClass<LocalDate> = LocalDate::class

    override fun convert(
        value: LocalDate,
        fieldName: String,
    ): CursorElement = CursorElement.TimestampValue(value.atStartOfDay(ZoneOffset.UTC).toInstant())
}

/** Converts a [Timestamp] to a [CursorElement.TimestampValue]. */
object TimestampCursorValueFactory : CursorValueFactory<Timestamp> {

    override val type: KClass<Timestamp> = Timestamp::class

    override fun convert(
        value: Timestamp,
        fieldName: String,
    ): CursorElement = CursorElement.TimestampValue(value.toInstant())
}
