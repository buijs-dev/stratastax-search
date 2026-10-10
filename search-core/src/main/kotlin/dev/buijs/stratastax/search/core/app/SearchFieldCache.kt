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
package dev.buijs.stratastax.search.core.app

import dev.buijs.stratastax.entity.core.api.EntityField
import dev.buijs.stratastax.search.core.spi.SearchConfiguration
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared, thread-safe resolver for looking up [EntityField]s on a [SearchConfiguration] by name,
 * case-insensitively. Used by both the filter (RSQL) and sort parsers so that field resolution
 * behaves identically, and is cached identically, across the search engine.
 *
 * Each distinct [SearchConfiguration] is expected to be a long-lived, effectively singleton
 * instance (e.g., one per searchable entity type). The cache below never evicts entries, so
 * constructing fresh [SearchConfiguration] instances per request would leak memory over the life of
 * the JVM.
 */
internal object SearchFieldCache {

    private val cache = ConcurrentHashMap<SearchConfiguration, Map<String, EntityField>>()

    /**
     * Resolves (and lazily caches) the lowercase field-name lookup map for [configuration].
     *
     * @param configuration The [SearchConfiguration] to resolve fields for.
     * @return A map of the lowercase field name to [EntityField].
     */
    internal fun fieldMapFor(configuration: SearchConfiguration): Map<String, EntityField> =
        cache.computeIfAbsent(configuration) { config ->
            config.fields.associateBy { it.name.lowercase() }
        }

    /**
     * Resolves a [EntityField] by case-insensitive [name] on [configuration].
     *
     * @param name The field name to resolve.
     * @param configuration The active [SearchConfiguration].
     * @return The matching [EntityField], or `null` if no field with that name exists.
     */
    internal fun find(
        name: String,
        configuration: SearchConfiguration,
    ): EntityField? = fieldMapFor(configuration)[name.lowercase()]
}
