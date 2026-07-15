package io.konifer.infrastructure.rules

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConfigurationRuleDefinitionRepositoryTest {
    @Test
    fun `fetch returns configured rule definition`() {
        val ruleDefinition = ruleDefinition(prompts = listOf("an image of a dog"))
        val repository =
            ConfigurationRuleDefinitionRepository(
                mapOf("dogs only" to ruleDefinition),
            )

        repository.fetch(RuleName("dogs only")) shouldBe ruleDefinition
    }

    @Test
    fun `fetch uses normalized rule name value`() {
        val ruleDefinition = ruleDefinition(prompts = listOf("an image of a dog"))
        val repository =
            ConfigurationRuleDefinitionRepository(
                mapOf("dogs only" to ruleDefinition),
            )

        repository.fetch(RuleName("DOGS ONLY")) shouldBe ruleDefinition
    }

    @Test
    fun `fetch throws when rule is not configured`() {
        val repository =
            ConfigurationRuleDefinitionRepository(
                mapOf("cats only" to ruleDefinition(prompts = listOf("an image of a cat"))),
            )

        shouldThrow<IllegalArgumentException> {
            repository.fetch(RuleName("dogs only"))
        }.message shouldBe "Rule with name: 'dogs only' not found"
    }

    private fun ruleDefinition(prompts: List<String>): RuleDefinition =
        RuleDefinition(
            prompts = prompts,
            threshold = RuleDefinitionThreshold(0.8),
        )
}
