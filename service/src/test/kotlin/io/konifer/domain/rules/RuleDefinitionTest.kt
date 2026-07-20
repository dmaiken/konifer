package io.konifer.domain.rules

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RuleDefinitionTest {
    @Test
    fun `constructor accepts up to 100 prompts`() {
        shouldNotThrowAny {
            RuleDefinition(
                name = RuleName("test-rule"),
                prompts = List(100) { "prompt-$it" },
                threshold = RuleDefinitionThreshold(0.5),
            )
        }
    }

    @Test
    fun `constructor rejects empty prompts`() {
        shouldThrow<IllegalArgumentException> {
            RuleDefinition(
                name = RuleName("test-rule"),
                prompts = emptyList(),
                threshold = RuleDefinitionThreshold(0.5),
            )
        }.message shouldBe "Rule prompts cannot be empty"
    }

    @Test
    fun `constructor rejects more than 100 prompts`() {
        shouldThrow<IllegalArgumentException> {
            RuleDefinition(
                name = RuleName("test-rule"),
                prompts = List(101) { "prompt-$it" },
                threshold = RuleDefinitionThreshold(0.5),
            )
        }.message shouldBe "Cannot have more than 100 prompts per rule definition"
    }

    @Test
    fun `constructor rejects definitions with duplicate prompts`() {
        shouldThrow<IllegalArgumentException> {
            RuleDefinition(
                name = RuleName("test-rule"),
                prompts = List(2) { "prompt" },
                threshold = RuleDefinitionThreshold(0.5),
            )
        }.message shouldBe "Rule prompts must be distinct"
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 1.0, 0.5])
    fun `threshold accepts values between zero and one inclusive`(threshold: Double) {
        RuleDefinitionThreshold(threshold).value shouldBe threshold
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1])
    fun `threshold rejects values outside zero and one`(threshold: Double) {
        shouldThrow<IllegalArgumentException> {
            RuleDefinitionThreshold(threshold)
        }.message shouldBe "Rule threshold must be between 0.0 and 1.0"
    }
}
