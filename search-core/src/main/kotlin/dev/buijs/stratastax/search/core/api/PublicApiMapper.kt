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

/** Maps a domain entity [T] to its REST representation [F]. */
fun interface PublicApiMapper<T, F> {
    /** Maps [entity] to its REST representation. */
    fun map(entity: T): F
}

/** Maps a [CursorPage] of domain entities to its REST list representation [L]. */
fun interface PublicApiListMapper<T, L> {
    /** Maps [page] to its REST list representation. */
    fun map(page: CursorPage<T>): L
}

/**
 * Maps a REST create-request DTO [F] to a create command [T].
 *
 * Named [mapCreate] rather than `map` on purpose: [PublicApiMapper.map] and the create/update
 * mappers all erase to the same JVM signature, so one class implementing them under a shared name
 * fails to compile ("Inherited platform declarations clash").
 */
fun interface PublicApiCreateMapper<F, T> {
    /** Maps the create [request] to a command. */
    fun mapCreate(request: F): T
}

/** Same as [PublicApiCreateMapper], for the update side. */
fun interface PublicApiUpdateMapper<F, T> {
    /** Maps the update [request] to a command. */
    fun mapUpdate(request: F): T
}
