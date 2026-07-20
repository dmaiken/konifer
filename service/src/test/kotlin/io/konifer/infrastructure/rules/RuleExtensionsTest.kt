package io.konifer.infrastructure.rules

import com.typesafe.config.ConfigFactory
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleExtensionsTest {
    @Test
    fun `getRuleDefinitions returns empty list when rule definitions are not configured`() {
        ConfigFactory.parseString("").getRuleDefinitions().shouldBeEmpty()
    }

    @Test
    fun `getRuleDefinitions returns named definitions from hocon map keys`() {
        val config =
            ConfigFactory.parseString(
                """
                rule-definitions {
                  joshua-tree {
                    prompts = [
                      "a joshua tree",
                      "joshua tree national park"
                    ]
                    threshold = 0.7
                  }
                }
                """.trimIndent(),
            )

        val ruleDefinitions = config.getRuleDefinitions()

        ruleDefinitions shouldHaveSize 1
        with(ruleDefinitions.single()) {
            name shouldBe RuleName("joshua-tree")
            prompts shouldBe listOf("a joshua tree", "joshua tree national park")
            threshold shouldBe RuleDefinitionThreshold(0.7)
        }
    }

    @Test
    fun `getRuleDefinitions returns multiple named definitions from hocon map keys`() {
        val config =
            ConfigFactory.parseString(
                """
                rule-definitions {
                  joshua-tree {
                    prompts = ["a joshua tree"]
                    threshold = 0.7
                  }
                  kermit-the-frog {
                    prompts = ["Kermit the frog"]
                    threshold = 0.8
                  }
                }
                """.trimIndent(),
            )

        val ruleDefinitionsByName = config.getRuleDefinitions().associateBy { it.name }

        ruleDefinitionsByName.keys shouldBe setOf(RuleName("joshua-tree"), RuleName("kermit-the-frog"))
        with(ruleDefinitionsByName.getValue(RuleName("joshua-tree"))) {
            prompts shouldBe listOf("a joshua tree")
            threshold shouldBe RuleDefinitionThreshold(0.7)
        }
        with(ruleDefinitionsByName.getValue(RuleName("kermit-the-frog"))) {
            prompts shouldBe listOf("Kermit the frog")
            threshold shouldBe RuleDefinitionThreshold(0.8)
        }
    }

    @Test
    fun `getRuleDefinitions rejects rule definitions that are not objects`() {
        val config =
            ConfigFactory.parseString(
                """
                rule-definitions {
                  joshua-tree = "not an object"
                }
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            config.getRuleDefinitions()
        }.message shouldBe "Configuration for rule 'joshua-tree' must be an object"
    }

    @Test
    fun `getRuleDefinitions rejects invalid rule names from hocon map keys`() {
        val config =
            ConfigFactory.parseString(
                """
                rule-definitions {
                  "this-rule-name-is-far-too-long-for-konifer" {
                    prompts = ["a joshua tree"]
                    threshold = 0.7
                  }
                }
                """.trimIndent(),
            )

        shouldThrow<IllegalArgumentException> {
            config.getRuleDefinitions()
        }.message shouldBe "Rule name cannot be longer than 32 characters"
    }
}
