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
 * Converts one raw value of type [T] into its [CursorElement] representation. This is the pluggable
 * per-type unit that [CursorValueFactoryRegistry] dispatches on.
 *
 * [Enum] and [Map] are reserved sentinel types: neither has a single common concrete class to key
 * on, so a factory registered under one is used for any enum or map value without an exact-type
 * match.
 */
interface CursorValueFactory<T : Any> {

    /**
     * The value type this factory handles, stated explicitly rather than inferred by reflection.
     */
    val type: KClass<T>

    /** Converts [value], belonging to the sort field [fieldName], into a [CursorElement]. */
    fun convert(
        value: T,
        fieldName: String,
    ): CursorElement
}

/**
 * Several [CursorValueFactory]s contributed as one unit, e.g. every factory a code generator wrote
 * for a module: one bean instead of one per factory. A registry built from beans registers each of
 * [factories] as if it were a bean of its own.
 */
interface CursorValueFactories {

    val factories: List<CursorValueFactory<*>>
}
