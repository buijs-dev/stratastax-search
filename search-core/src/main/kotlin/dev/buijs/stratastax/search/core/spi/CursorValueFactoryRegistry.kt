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
 * Immutable, thread-safe (no mutation once built) lookup table from a raw value's runtime [KClass]
 * to the [CursorValueFactory] that converts it - what [Any?.toCursorElement] consults instead of
 * its old hardcoded `when`.
 *
 * Not CDI-aware itself - quarkus-starter's `CursorValueFactoryProducer` builds a merged instance
 * (its own default beans, plus any consumer-provided `CursorValueFactory` beans, including
 * overrides of a default) from discovered CDI beans and threads it into
 * `buildEntityBinding(registry) { }` / `fuseEntityBindings(registry, ...)`.
 */
class CursorValueFactoryRegistry
private constructor(private val byType: Map<KClass<out Any>, CursorValueFactory<out Any>>) {

    /**
     * Converts [value] via the factory registered for its exact runtime type, falling back to the
     * [Enum]/[Map] sentinel entries (see [CursorValueFactory]'s kdoc) when [value] is an
     * `Enum<*>`/`Map<*, *>` with no exact match of its own. Returns `null` - never throws - when no
     * factory matches; the caller ([Any?.toCursorElement]) decides how to report that.
     */
    @Suppress("UNCHECKED_CAST")
    fun convert(value: Any, fieldName: String): CursorElement? {
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
         *   registered for that exact type - two factories for the same type within one registry is
         *   always a configuration mistake, never an intentional "last one wins".
         */
        fun <T : Any> register(factory: CursorValueFactory<T>): Builder = apply {
            validate(!entries.containsKey(factory.type)) {
                "A CursorValueFactory is already registered for " +
                    "${factory.type.simpleName} - registering a second one " +
                    "for the same type is not supported."
            }
            entries[factory.type] = factory
        }

        fun build(): CursorValueFactoryRegistry = CursorValueFactoryRegistry(entries.toMap())
    }

    companion object {
        fun builder(): Builder = Builder()

        /**
         * Covers exactly the types [Any?.toCursorElement]'s own hardcoded `when` used to handle
         * directly - String, UUID, Enum, Boolean, every numeric type, every supported temporal
         * type - so behavior for every currently-supported type is unchanged by default.
         */
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

/**
 * The 15 default [CursorValueFactory] implementations, one per standard type
 * - each holding exactly the conversion [Any?.toCursorElement]'s old hardcoded `when` branch had
 *   for that type. Not `internal`: quarkus-starter (a separate module) reuses these same instances
 *   for its own `@DefaultBean` beans instead of re-implementing the conversions.
 */
object StringCursorValueFactory : CursorValueFactory<String> {
    override val type: KClass<String> = String::class

    override fun convert(value: String, fieldName: String): CursorElement =
        CursorElement.StringValue(value)
}

object UuidCursorValueFactory : CursorValueFactory<UUID> {
    override val type: KClass<UUID> = UUID::class

    override fun convert(value: UUID, fieldName: String): CursorElement =
        CursorElement.StringValue(value.toString())
}

/** Registered under the [Enum::class] sentinel - see [CursorValueFactory]'s kdoc. */
object EnumCursorValueFactory : CursorValueFactory<Enum<*>> {
    override val type: KClass<Enum<*>> = Enum::class

    override fun convert(value: Enum<*>, fieldName: String): CursorElement =
        CursorElement.StringValue(value.name)
}

object BooleanCursorValueFactory : CursorValueFactory<Boolean> {
    override val type: KClass<Boolean> = Boolean::class

    override fun convert(value: Boolean, fieldName: String): CursorElement =
        CursorElement.BooleanValue(value)
}

object BigDecimalCursorValueFactory : CursorValueFactory<BigDecimal> {
    override val type: KClass<BigDecimal> = BigDecimal::class

    override fun convert(value: BigDecimal, fieldName: String): CursorElement =
        CursorElement.NumberValue(value)
}

object IntCursorValueFactory : CursorValueFactory<Int> {
    override val type: KClass<Int> = Int::class

    override fun convert(value: Int, fieldName: String): CursorElement =
        CursorElement.NumberValue(value.toBigDecimal())
}

object LongCursorValueFactory : CursorValueFactory<Long> {
    override val type: KClass<Long> = Long::class

    override fun convert(value: Long, fieldName: String): CursorElement =
        CursorElement.NumberValue(value.toBigDecimal())
}

object DoubleCursorValueFactory : CursorValueFactory<Double> {
    override val type: KClass<Double> = Double::class

    override fun convert(value: Double, fieldName: String): CursorElement =
        CursorElement.NumberValue(value.toBigDecimal())
}

object FloatCursorValueFactory : CursorValueFactory<Float> {
    override val type: KClass<Float> = Float::class

    override fun convert(value: Float, fieldName: String): CursorElement =
        CursorElement.NumberValue(value.toDouble().toBigDecimal())
}

object ShortCursorValueFactory : CursorValueFactory<Short> {
    override val type: KClass<Short> = Short::class

    override fun convert(value: Short, fieldName: String): CursorElement =
        CursorElement.NumberValue(value.toInt().toBigDecimal())
}

object InstantCursorValueFactory : CursorValueFactory<Instant> {
    override val type: KClass<Instant> = Instant::class

    override fun convert(value: Instant, fieldName: String): CursorElement =
        CursorElement.TimestampValue(value)
}

object OffsetDateTimeCursorValueFactory : CursorValueFactory<OffsetDateTime> {
    override val type: KClass<OffsetDateTime> = OffsetDateTime::class

    override fun convert(value: OffsetDateTime, fieldName: String): CursorElement =
        CursorElement.TimestampValue(value.toInstant())
}

object LocalDateTimeCursorValueFactory : CursorValueFactory<LocalDateTime> {
    override val type: KClass<LocalDateTime> = LocalDateTime::class

    override fun convert(value: LocalDateTime, fieldName: String): CursorElement =
        CursorElement.TimestampValue(value.toInstant(ZoneOffset.UTC))
}

object LocalDateCursorValueFactory : CursorValueFactory<LocalDate> {
    override val type: KClass<LocalDate> = LocalDate::class

    override fun convert(value: LocalDate, fieldName: String): CursorElement =
        CursorElement.TimestampValue(value.atStartOfDay(ZoneOffset.UTC).toInstant())
}

object TimestampCursorValueFactory : CursorValueFactory<Timestamp> {
    override val type: KClass<Timestamp> = Timestamp::class

    override fun convert(value: Timestamp, fieldName: String): CursorElement =
        CursorElement.TimestampValue(value.toInstant())
}
