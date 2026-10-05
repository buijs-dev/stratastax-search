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
 * The cardinality of a [FieldGroup]'s relation to its parent (the root entity being searched, or an
 * ancestor group for a nested group).
 */
sealed interface FieldGroupRelation {
    data object OneToOne : FieldGroupRelation

    data object OneToMany : FieldGroupRelation
}

/**
 * A named, logical grouping of fields that belong to a related domain model (e.g. `"game"` for a
 * `Match`'s `GameRef`, or `"participantsGhostPlayer"` for a `GhostPlayerRef` nested inside a
 * `Match`'s participants).
 *
 * Purely descriptive at this level, it carries no physical join information. A backend-specific DSL
 * (e.g., persistence-reactive) attaches its own physical relation details (columns) alongside this.
 *
 * @property name The group's own camelCase identifier, e.g. `"participantsGhostPlayer"` for a group
 *   nested inside `"participants"` - built the same way as a field's name (see
 *   [toSearchFieldName]), so it's always a plain, dot-free identifier, safe to use anywhere a field
 *   name would be (e.g., as a JOOQ `MULTISET` alias in persistence-reactive). Never parse this for
 *   structure - use [parentName].
 * @property domainType The related domain model type this group's fields describe.
 * @property relation Whether [domainType] relates to its parent as one (
 *   [FieldGroupRelation.OneToOne]) or many ([FieldGroupRelation.OneToMany]).
 * @property fieldNames The camelCase [name]s (see [toSearchFieldName]) of the fields registered
 *   directly under this group.
 * @property parentName The enclosing group's [name], or `null` if this group is declared directly
 *   on the root entity. Set explicitly at DSL construction time by whichever builder is lexically
 *   enclosing this group's declaration
 *     - the only way to navigate group hierarchy; [name] never encodes it. See `JooqGroupTree` in
 *       persistence-reactive, which resolves parent/child groups solely via this field.
 */
data class FieldGroup(
    val name: String,
    val domainType: KClass<*>,
    val relation: FieldGroupRelation,
    val fieldNames: List<String>,
    val parentName: String? = null,
)
