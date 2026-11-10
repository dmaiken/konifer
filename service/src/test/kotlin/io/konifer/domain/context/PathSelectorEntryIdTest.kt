package io.konifer.domain.context

import io.konifer.domain.asset.toEntryId
import io.konifer.domain.context.selector.DeleteSelection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PathSelectorEntryIdTest {
    @ParameterizedTest
    @ValueSource(longs = [0, Long.MAX_VALUE])
    fun `wraps valid entry selectors`(id: Long) {
        PathSelectorExtractor.extractQuerySelectors("entry/$id", Parameters.Empty).entryId shouldBe id.toEntryId()
        PathSelectorExtractor.extractQuerySelectors("entry/$id/info", Parameters.Empty).entryId shouldBe id.toEntryId()
        PathSelectorExtractor.extractDeleteSelectors("entry/$id", Parameters.Empty) shouldBe DeleteSelection.Entry(id.toEntryId())
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "abc", "9223372036854775808"])
    fun `invalid entry selectors retain their errors`(id: String) {
        listOf("entry/$id", "entry/$id/info").forEach { selector ->
            shouldThrow<InvalidQuerySelectorsException> {
                PathSelectorExtractor.extractQuerySelectors(selector, Parameters.Empty)
            }.message shouldBe "Invalid query modifiers: ${selector.uppercase().split('/')}"
        }

        shouldThrow<InvalidDeleteSelectorsException> {
            PathSelectorExtractor.extractDeleteSelectors("entry/$id", Parameters.Empty)
        }.message shouldBe "Invalid delete modifiers: ${listOf("ENTRY", id.uppercase())}"
    }
}
