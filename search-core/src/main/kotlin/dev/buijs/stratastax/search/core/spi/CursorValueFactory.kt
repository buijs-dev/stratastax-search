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

import kotlin.reflect.KClass

/**
 * Converts one raw value of type [T] into its [CursorElement] representation - the pluggable
 * per-type unit [CursorValueFactoryRegistry] dispatches on instead of [Any?.toCursorElement]'s old
 * hardcoded `when`.
 *
 * [type] is stated explicitly rather than inferred via reflection over the implementer's own
 * generic supertype, so a CDI container (quarkus-starter) can key an aggregated registry purely off
 * each bean's own declared [type], and so a consumer can override exactly one standard type's
 * conversion by registering one bean of this shape for it - see quarkus-starter's
 * `CursorValueFactoryProducer`/`@DefaultBean`-per-type wiring.
 *
 * [Enum::class]/[Map::class] are reserved sentinel [type]s - neither `Enum` nor `Map` instances
 * have one single common concrete `KClass` to key an exact-type registry entry on (an `Enum<*>`'s
 * own class is generated per enum type; a `Map<String, Any?>` literal's own runtime class varies by
 * size
 * - `EmptyMap`, a singleton-map class, `LinkedHashMap`, ...), so a factory registered under either
 *   sentinel is consulted whenever the value is an `Enum<*>`/`Map<*, *>` and no exact match for its
 *   own concrete class exists.
 */
interface CursorValueFactory<T : Any> {
    val type: KClass<T>

    fun convert(value: T, fieldName: String): CursorElement
}
