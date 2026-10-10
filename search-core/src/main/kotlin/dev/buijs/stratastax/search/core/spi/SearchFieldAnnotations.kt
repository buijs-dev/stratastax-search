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

/**
 * Marks a constructor property as filterable - the annotation-based equivalent of `publicApi {
 * filter() }`. Named without an `Api`/`Persistence` prefix because it drives both sides: the
 * REST-facing filter surface *and* the persistence-side default-filter behavior below - neither
 * `rest-core` nor `persistence-core` alone.
 *
 * [fixedValue], when non-blank, switches this to the annotation-based equivalent of `persistence {
 * fixedValue(CursorElement.XxxValue(...)) } }` instead - an always-applied filter the caller can't
 * override (e.g. exclude soft-deleted rows), rather than a caller-controlled one; `publicApi {
 * filter() }` is then never rendered for this field (the two are mutually exclusive - a field with
 * a fixed value can't meaningfully be filtered by the caller too, same as the hand-written DSL's
 * own `fixedValue(...)`). Written the same way an RSQL filter's own *value* token would be: the
 * bare word `null` (case-insensitive) for [CursorElement.NullValue], `true`/`false` for
 * [CursorElement.BooleanValue], a plain number for [CursorElement.NumberValue], anything else taken
 * as a literal [CursorElement.StringValue] - see gradle-plugin's `AnnotationBindingRenderer` for
 * exactly how each is resolved. No annotation-based way (yet) to express
 * [CursorElement.TimestampValue]/[CursorElement.ListValue] - use the hand-written DSL's own
 * `fixedValue(...)` call directly for those.
 *
 * [alias], when non-blank, is the annotation-based equivalent of `publicApi { filter { name("...")
 * } }` - overrides this field's own RSQL/search-exposed key (e.g. `?filter=name==foo` instead of
 * `?filter=displayName==foo`), independent of [fixedValue]. It renames this field's *search*
 * identity (`fieldNames`/`boundNames`/`columnAccessors`, i.e. what a caller filters/ sorts by and
 * what `RowMapper` resolves), and `createBindings`/ `updateBindings` (the *command*-property side,
 * e.g. what `${Entity}CreateMapper` reads `command.x` from) - both looked up at codegen time
 * against the generated `${Entity}Search` bean's own runtime field property, which is registered
 * under the alias, not this field's real name. The REST-DTO-property side
 * (`createInputBindings`/`updateInputBindings`/ `publicApiBindings`, populated by
 * `OpenApiEntityCorrelator` matching this entity's fields against an OpenAPI schema's own
 * properties) is *also* keyed by the alias for consistency with the maps above, but its *matching*
 * stays permissive either way: a schema property matching this field's real name (e.g.
 * `displayName`) binds exactly as it would with no alias at all, and one matching the alias (e.g.
 * `name`) binds too - either is accepted, so a DTO can freely use whichever name reads better for
 * its own REST shape. See gradle-plugin's `AnnotationMetadataMerger`/`OpenApiEntityCorrelator` for
 * exactly where each of these is applied.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Filter(
    val fixedValue: String = "",
    val alias: String = "",
)

/**
 * Marks a constructor property as sortable - the annotation-based equivalent of `publicApi { sort {
 * tiebreaker(); ascending()/descending() } }`. Named without an `Api` prefix for the same reason as
 * [Filter] - the persistence side needs this too (e.g. to know the tiebreaker column for stable
 * ordering), not just the REST-facing sort surface.
 *
 * @property tiebreaker whether this is the entity's single required `sort { tiebreaker() }` field
 *   (cursor-based pagination needs exactly one across the whole entity).
 * @property direction the field's default sort direction.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Sort(
    val tiebreaker: Boolean = false,
    val direction: SortDirection = SortDirection.ASC,
)
