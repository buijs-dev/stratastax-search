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
package dev.buijs.stratastax.search.core.api

// TODO deze interfaces horen niet in deze module

/**
 * Maps a domain entity to its REST representation - implemented by a generated
 * `${Entity}PublicApiMapper`/`${GroupDomainType}PublicApiMapper` (see the
 * `publicApiField(...)`/`publicApi<...>()` DSL surface in search-jooq's
 * `EntityBindingDsl`/`JooqFieldBuilder`).
 */
fun interface PublicApiMapper<T, F> {
    fun map(entity: T): F
}

/**
 * Maps a [CursorPage] of domain entities to its REST list representation - implemented by a
 * generated `${Entity}PublicApiMapper`, only when `publicApiList<...>()` was declared alongside
 * `publicApi<...>()`.
 */
fun interface PublicApiListMapper<T, L> {
    fun map(page: CursorPage<T>): L
}

/**
 * Maps a REST create-request DTO to a `create(...)` command - implemented by the same generated
 * `${Entity}PublicApiMapper` as [PublicApiMapper]/ [PublicApiListMapper], only when the entity
 * declares a create request DTO (see the `publicApi { create<F> { } }` DSL surface in search-jooq's
 * `JooqFieldBuilder`). A distinct interface from [PublicApiUpdateMapper] - even though shaped
 * identically - because a single class cannot implement the same generic interface twice with
 * different type arguments (JVM erasure).
 *
 * Named [mapCreate], not `map` - a real reported bug: [PublicApiMapper.map],
 * [PublicApiCreateMapper]'s own method and [PublicApiUpdateMapper]'s own method each take one bare,
 * unbounded generic type parameter as their sole argument, so all three erase to the identical JVM
 * descriptor `map(Ljava/lang/Object;)Ljava/lang/Object;` regardless of which interface declares
 * it - a class implementing all three under that shared name fails to compile ("Inherited platform
 * declarations clash"). Unlike [PublicApiListMapper] (whose `CursorPage<T>` parameter erases to the
 * concrete `CursorPage` class, giving it a genuinely different bridge signature for free),
 * create/update have no such wrapper type, so each needs its own method name instead.
 */
fun interface PublicApiCreateMapper<F, T> {
    fun mapCreate(request: F): T
}

/** Same as [PublicApiCreateMapper], for the update side. */
fun interface PublicApiUpdateMapper<F, T> {
    fun mapUpdate(request: F): T
}
