package io.konifer.domain.rules

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RuleDefinitionTest {
    @Test
    fun `constructor accepts up to 100 prompts`() {
        shouldNotThrowAny {
            RuleDefinition(
                prompts = List(100) { "prompt-$it" },
                threshold = RuleDefinitionThreshold(0.5),
            )
        }
    }

    @Test
    fun `constructor rejects empty prompts`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                RuleDefinition(
                    prompts = emptyList(),
                    threshold = RuleDefinitionThreshold(0.5),
                )
            }

        exception.message shouldBe "Rule prompts cannot be empty"
    }

    @Test
    fun `constructor rejects more than 100 prompts`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                RuleDefinition(
                    prompts = List(101) { "prompt-$it" },
                    threshold = RuleDefinitionThreshold(0.5),
                )
            }

        exception.message shouldBe "Cannot have more than 100 prompts per rule definition"
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 1.0, 0.5])
    fun `threshold accepts values between zero and one inclusive`(threshold: Double) {
        RuleDefinitionThreshold(threshold).value shouldBe threshold
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1])
    fun `threshold rejects values outside zero and one`(threshold: Double) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                RuleDefinitionThreshold(threshold)
            }

        exception.message shouldBe "Rule threshold must be between 0.0 and 1.0"
    }

    @Test
    fun `deserialization uses configured property names`() {
        val ruleDefinition =
            Json.decodeFromString<RuleDefinition>(
                """
                {
                    "prompts": ["no nudity", "no violence"],
                    "threshold": 0.8
                }
                """.trimIndent(),
            )

        ruleDefinition shouldBe
            RuleDefinition(
                prompts = listOf("no nudity", "no violence"),
                threshold = RuleDefinitionThreshold(0.8),
            )
    }

    @Test
    fun `serialization writes configured property names`() {
        Json.encodeToString(
            RuleDefinition(
                prompts = listOf("no nudity", "no violence"),
                threshold = RuleDefinitionThreshold(0.8),
            ),
        ) shouldBe """{"prompts":["no nudity","no violence"],"threshold":0.8}"""
    }
}
