package io.konifer.domain.rules

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RuleDefinitionsTest {
    @Test
    fun `cannot have more than 10 rule definitions`() {
        val definitions =
            List(11) {
                RuleDefinition(
                    name = RuleName("test-rule"),
                    prompts = listOf(RulePrompt("prompt-$it")),
                    threshold = RuleDefinitionThreshold(0.5),
                )
            }

        shouldThrow<IllegalArgumentException> {
            RuleDefinitions(definitions)
        }.message shouldBe "Maximum of 10 rule definitions allowed per request"
    }

    @Test
    fun `cannot have empty rule definitions`() {
        shouldThrow<IllegalArgumentException> {
            RuleDefinitions(emptyList())
        }.message shouldBe "At least one rule request is required"
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 9])
    fun `can have definitions between min and max`(definitionAmount: Int) {
        val definitions =
            List(definitionAmount) {
                RuleDefinition(
                    name = RuleName("test-rule"),
                    prompts = listOf(RulePrompt("prompt-$it")),
                    threshold = RuleDefinitionThreshold(0.5),
                )
            }

        shouldNotThrowAny {
            RuleDefinitions(definitions)
        }
    }
}
