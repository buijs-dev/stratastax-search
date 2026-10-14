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
import dev.buijs.stratastax.search.testdata.Status
import dev.buijs.stratastax.search.testdata.WrappedId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class CursorValueFactoryRegistryTest {

    @Test
    fun `default registry converts every standard type exactly like the old hardcoded dispatch`() {
        val registry = CursorValueFactoryRegistry.default
        assertThat(registry.convert("hi", "x"))
            .isEqualTo(CursorElement.StringValue("hi"))
        assertThat(registry.convert(5, "x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(5)))
        assertThat(registry.convert(Status.ACTIVE, "x"))
            .isEqualTo(CursorElement.StringValue("ACTIVE"))
    }

    @Test
    fun `convert returns null when no factory is registered for the value's type`() {
        val registry = CursorValueFactoryRegistry.builder().build()
        assertThat(registry.convert(WrappedId(1), "x")).isNull()
    }

    @Test
    fun `a registered factory for a custom type is used`() {
        val wrapperFactory =
            factory(WrappedId::class) {
                CursorElement.NumberValue(it.value.toBigDecimal())
            }

        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(wrapperFactory)
                .build()

        assertThat(registry.convert(WrappedId(42), "x"))
            .isEqualTo(CursorElement.NumberValue(BigDecimal.valueOf(42)))
    }

    @Test
    fun `registering two factories for the same type throws`() {
        assertThatThrownBy {
            CursorValueFactoryRegistry
                .builder()
                .register(factory(String::class) { CursorElement.StringValue(it) })
                .register(factory(String::class) { CursorElement.StringValue(it) })
        }.isInstanceOf(SearchException::class.java).hasMessageContaining("String")
    }

    @Test
    fun `an enum with no exact match falls back to the Enum sentinel entry`() {
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(factory(Enum::class) { CursorElement.StringValue("enum:${it.name}") })
                .build()

        assertThat(registry.convert(Status.ACTIVE, "x"))
            .isEqualTo(CursorElement.StringValue("enum:ACTIVE"))
    }

    @Test
    fun `an exact match for a concrete enum type wins over the Enum sentinel`() {
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(factory(Enum::class) { CursorElement.StringValue("sentinel") })
                .register(factory(Status::class) { CursorElement.StringValue("exact:${it.name}") })
                .build()

        assertThat(registry.convert(Status.ACTIVE, "x"))
            .isEqualTo(CursorElement.StringValue("exact:ACTIVE"))
    }

    @Test
    fun `a Map with no exact match falls back to the Map sentinel entry`() {
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(factory(Map::class) { CursorElement.StringValue("map:${it.size}") })
                .build()

        assertThat(registry.convert(mapOf("color" to "red"), "x"))
            .isEqualTo(CursorElement.StringValue("map:1"))
    }

    @Test
    fun `an empty Map still falls back to the Map sentinel entry`() {
        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(factory(Map::class) { CursorElement.StringValue("map:${it.size}") })
                .build()

        assertThat(registry.convert(emptyMap<String, Any?>(), "x"))
            .isEqualTo(CursorElement.StringValue("map:0"))
    }

    @Test
    fun `an exact match for a concrete Map type wins over the Map sentinel`() {
        val linkedMapFactory =
            factory(LinkedHashMap::class) {
                CursorElement.StringValue("exact:${it.size}")
            }

        val registry =
            CursorValueFactoryRegistry
                .builder()
                .register(factory(Map::class) { CursorElement.StringValue("sentinel") })
                .register(linkedMapFactory)
                .build()

        assertThat(registry.convert(linkedMapOf("color" to "red"), "x"))
            .isEqualTo(CursorElement.StringValue("exact:1"))
    }

    private fun <T : Any> factory(
        forType: kotlin.reflect.KClass<T>,
        block: (T) -> CursorElement,
    ): CursorValueFactory<T> =
        object : CursorValueFactory<T> {

            override val type = forType

            override fun convert(
                value: T,
                fieldName: String,
            ): CursorElement = block(value)
        }
}
