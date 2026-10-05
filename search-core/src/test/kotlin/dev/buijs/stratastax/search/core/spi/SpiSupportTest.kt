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
import dev.buijs.stratastax.entity.core.api.EntityFieldType
import dev.buijs.stratastax.search.core.api.SearchException
import dev.buijs.stratastax.search.core.api.Sort
import dev.buijs.stratastax.search.core.app.valueOrThrow
import dev.buijs.stratastax.search.testdata.nameField
import dev.buijs.stratastax.search.testdata.nameSortField
import dev.buijs.stratastax.search.testdata.scoreField
import dev.buijs.stratastax.search.testdata.testSearchConfiguration
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SpiSupportTest {

    @Nested
    inner class SearchFieldNames {

        @Test
        fun `a plain name is unchanged`() {
            assertThat("name".toSearchFieldName()).isEqualTo("name")
        }

        @Test
        fun `a dot-qualified path becomes camelCase`() {
            assertThat("game.expansions.name".toSearchFieldName()).isEqualTo("gameExpansionsName")
        }
    }

    @Nested
    inner class SortFields {

        @Test
        fun `asc and desc build a SortField`() {
            assertThat(nameField.asc()).isEqualTo(SortField(nameField, SortDirection.ASC))
            assertThat(nameField.desc()).isEqualTo(SortField(nameField, SortDirection.DESC))
        }

        @Test
        fun `direction is parsed case-insensitively`() {
            assertThat(SortDirection.of("asc", "name")).isEqualTo(SortDirection.ASC)
            assertThat(SortDirection.of("Desc", "name")).isEqualTo(SortDirection.DESC)
        }

        @Test
        fun `an unknown direction is rejected`() {
            assertThatThrownBy { SortDirection.of("sideways", "name") }
                .isInstanceOf(SearchException::class.java)
                .hasMessage(
                    "Invalid sort direction 'sideways' for field 'name', expected 'ASC' or 'DESC'"
                )
        }
    }

    @Nested
    inner class CursorKeyExtractors {

        private val extractors: Map<String, (String) -> CursorElement> =
            mapOf(
                "name" to { CursorElement.StringValue(it) },
                "score" to { CursorElement.NumberValue.of(it.length) },
            )

        @Test
        fun `elements follow the order of the sort, not of the map`() {
            val sort = Sort(listOf(scoreField.asc(), nameField.desc()))

            assertThat(extractors.filterElementsFor("abc", sort))
                .containsExactly(CursorElement.NumberValue.of(3), CursorElement.StringValue("abc"))
        }

        @Test
        fun `a sort field without an extractor is rejected`() {
            val sort = Sort(listOf(EntityField("other", EntityFieldType.STRING).asc()))

            assertThatThrownBy { extractors.filterElementsFor("abc", sort) }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining(
                    "No cursorValue extractor configured for sortable field 'other'"
                )
        }

        @Test
        fun `the functional interface can be implemented by a lambda`() {
            val extractor =
                CursorKeyExtractor<String> { item, sort ->
                    extractors.filterElementsFor(item, sort)
                }

            assertThat(extractor.elementsFor("ab", Sort(listOf(nameSortField))))
                .containsExactly(CursorElement.StringValue("ab"))
        }
    }

    @Nested
    inner class Configuration {

        @Test
        fun `standard configuration has standard limits and no groups by default`() {
            val configuration =
                StandardSearchConfiguration(
                    defaultSort = listOf(nameSortField),
                    fields = listOf(nameField),
                    tiebreakerField = nameField,
                )

            assertThat(configuration.defaultLimit).isEqualTo(STANDARD_DEFAULT_LIMIT)
            assertThat(configuration.maxLimit).isEqualTo(STANDARD_MAX_LIMIT)
            assertThat(configuration.groups).isEmpty()
            assertThat(configuration.fields).containsExactly(nameField)
            assertThat(configuration.tiebreakerField).isEqualTo(nameField)
        }

        @Test
        fun `standard configuration accepts its own limits and groups`() {
            val group =
                FieldGroup("game", String::class, FieldGroupRelation.OneToOne, listOf("gameName"))

            val configuration =
                StandardSearchConfiguration(
                    defaultLimit = 5,
                    maxLimit = 7,
                    defaultSort = listOf(nameSortField),
                    fields = listOf(nameField),
                    tiebreakerField = nameField,
                    groups = listOf(group),
                )

            assertThat(configuration.defaultLimit).isEqualTo(5)
            assertThat(configuration.maxLimit).isEqualTo(7)
            assertThat(configuration.groups).containsExactly(group)
        }

        @Test
        fun `groups default to empty for any other implementation`() {
            assertThat(testSearchConfiguration.groups).isEmpty()
        }
    }

    @Nested
    inner class FieldGroups {

        @Test
        fun `a group has no parent unless one is given`() {
            val root = FieldGroup("game", String::class, FieldGroupRelation.OneToMany, listOf("a"))
            val child =
                root.copy(name = "gameExpansions", parentName = root.name, fieldNames = emptyList())

            assertThat(root.parentName).isNull()
            assertThat(root.relation).isEqualTo(FieldGroupRelation.OneToMany)
            assertThat(root.domainType).isEqualTo(String::class as KClass<*>)
            assertThat(child.parentName).isEqualTo("game")
        }
    }

    @Nested
    inner class FieldRegistry {

        private val fields = object : SearchFieldRegistry(testSearchConfiguration) {}

        private val name by fields
        private val score by testSearchConfiguration.asFields()
        private val missing by fields

        @Test
        fun `a delegated property resolves the field with the same name`() {
            assertThat(name).isEqualTo(nameField)
            assertThat(score).isEqualTo(scoreField)
        }

        @Test
        fun `a property without a configured field is rejected`() {
            assertThatThrownBy { missing }
                .isInstanceOf(SearchException::class.java)
                .hasMessageContaining("No field 'missing' configured in this SearchConfiguration")
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `valueOrThrow returns the value`() {
            assertThat(valueOrThrow("x") { "unused" }).isEqualTo("x")
        }

        @Test
        fun `valueOrThrow throws with the lazy message on null`() {
            assertThatThrownBy { valueOrThrow<String>(null) { "was null" } }
                .isInstanceOf(SearchException::class.java)
                .hasMessage("was null")
        }
    }
}
