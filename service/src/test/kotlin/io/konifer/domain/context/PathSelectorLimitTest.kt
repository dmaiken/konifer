package io.konifer.domain.context

import io.konifer.domain.context.InvalidRequestException
import io.konifer.domain.context.selector.DeleteSelection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PathSelectorLimitTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "0", "-2", "2147483648", "-2147483649"])
    fun `invalid explicit limits never fall back to defaults`(value: String) {
        val parameters = Parameters.build { append("limit", value) }
        shouldThrow<InvalidRequestException> { PathSelectorExtractor.extractQuerySelectors("entries", parameters) }
        shouldThrow<InvalidRequestException> { PathSelectorExtractor.extractDeleteSelectors(null, parameters) }
    }

    @Test
    fun `repeated limits are rejected`() {
        val parameters =
            Parameters.build {
                append("limit", "-1")
                append("limit", "1")
            }
        shouldThrow<InvalidRequestException> { PathSelectorExtractor.extractQuerySelectors("entries", parameters) }
        shouldThrow<InvalidRequestException> { PathSelectorExtractor.extractDeleteSelectors(null, parameters) }
    }

    @Test
    fun `only entries can request an unlimited GET selection`() {
        val parameters = Parameters.build { append("limit", "-1") }
        PathSelectorExtractor.extractQuerySelectors("entries", parameters).limit shouldBe Limit.unlimited
        listOf(null, "link", "info", "content", "download", "redirect").forEach {
            shouldThrow<InvalidQuerySelectorsException> { PathSelectorExtractor.extractQuerySelectors(it, parameters) }
        }
    }

    @Test
    fun `deletion requires an explicit target`() {
        shouldThrow<InvalidDeleteSelectorsException> { PathSelectorExtractor.extractDeleteSelectors(null, Parameters.Empty) }
        PathSelectorExtractor.extractDeleteSelectors(null, Parameters.build { append("limit", "-1") }) shouldBe DeleteSelection.AllAtPath
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "20", "-1"])
    fun `selectors cannot be combined with any limit`(value: String) {
        val parameters = Parameters.build { append("limit", value) }
        listOf("entry/42", "recursive", "new", "modified").forEach {
            shouldThrow<InvalidDeleteSelectorsException> { PathSelectorExtractor.extractDeleteSelectors(it, parameters) }
        }
    }
}
