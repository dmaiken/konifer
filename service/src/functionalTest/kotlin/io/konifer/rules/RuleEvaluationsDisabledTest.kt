package io.konifer.rules

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import org.junit.jupiter.api.Test

class RuleEvaluationsDisabledTest : BaseFunctionalTest() {
    @Test
    fun `if rule evaluation API is disabled then 404 is returned`() =
        testInMemory(
            """
            api.rule-evaluation.enabled = false
            """.trimIndent(),
        ) {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .evaluateRules(
                    format = attributes.format,
                    bytes = image,
                    request =
                        EvaluateRuleDefinitionsRequest(
                            definitions =
                                listOf(
                                    RuleDefinitionRequest(
                                        name = "one",
                                        prompts =
                                            listOf(
                                                "a joshua tree",
                                            ),
                                        threshold = 0.99,
                                    ),
                                ),
                        ),
                ).shouldHaveHttpError(404)
        }

    @Test
    fun `if rule evaluation API is disabled by default`() =
        testInMemory {
            val (image, attributes) = ImageFactory.testImage()

            konifer()
                .evaluateRules(
                    format = attributes.format,
                    bytes = image,
                    request =
                        EvaluateRuleDefinitionsRequest(
                            definitions =
                                listOf(
                                    RuleDefinitionRequest(
                                        name = "one",
                                        prompts =
                                            listOf(
                                                "a joshua tree",
                                            ),
                                        threshold = 0.99,
                                    ),
                                ),
                        ),
                ).shouldHaveHttpError(404)
        }
}
