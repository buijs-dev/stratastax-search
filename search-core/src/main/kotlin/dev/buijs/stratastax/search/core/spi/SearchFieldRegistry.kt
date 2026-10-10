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

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.search.core.app.valueOrThrow
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Property delegate that resolves a [EntityField] from the [SearchConfiguration] by the name of the
 * delegating property, e.g. `val name by configuration.asFields()`.
 *
 * @throws dev.buijs.stratastax.search.core.api.SearchException On access if no field with that name
 *   is configured.
 */
open class SearchFieldRegistry(
    private val configuration: SearchConfiguration,
) : ReadOnlyProperty<Any?, EntityField> {

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): EntityField =
        valueOrThrow(configuration.fields.firstOrNull { it.name == property.name }) {
            "No field '${property.name}' configured in this SearchConfiguration. " +
                "Check that field(\"${property.name}\") { ... } " +
                "in the DSL matches this property name exactly."
        }
}

/** Creates a [SearchFieldRegistry] delegate for this configuration. */
fun SearchConfiguration.asFields(): SearchFieldRegistry = SearchFieldRegistry(this)
