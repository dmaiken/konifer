package io.konifer.rules

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.KoniferTestHandle
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.RuleDefinitionRequest
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemoryHandle
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvaluateRuleDefinitionsTest : BaseFunctionalTest() {
    private lateinit var handle: KoniferTestHandle

    @BeforeAll
    fun startKonifer() {
        handle =
            testInMemoryHandle(
                """
                api.rule-evaluation.enabled = true
                """.trimIndent(),
            )
        handle.start()
    }

    @AfterAll
    fun stopKonifer() {
        handle.close()
    }

    @Test
    fun `can evaluate one rule definition that matches`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
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
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.7,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 1
            response.results.forExactly(1) {
                it.name shouldBe "one"
                it.matched shouldBe true
                it.threshold shouldBe 0.7
                it.score shouldBeGreaterThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
        }

    @Test
    fun `can evaluate one rule definition that does not match`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
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
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.99,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 1
            response.results.forExactly(1) {
                it.name shouldBe "one"
                it.matched shouldBe false
                it.threshold shouldBe 0.99
                it.score shouldBeLessThan 0.99
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
        }

    @Test
    fun `can evaluate multiple rule definitions`() =
        handle.test {
            val (image, attributes) = ImageFactory.testImage()

            val response =
                konifer()
                    .evaluateRules(
                        format = attributes.format,
                        bytes = image,
                        request =
                            EvaluateRuleDefinitionsRequest(
                                definitions =
                                    listOf(
                                        RuleDefinitionRequest(
                                            name = "matches",
                                            prompts =
                                                listOf(
                                                    "a joshua tree",
                                                    "a tree",
                                                    "joshua tree national park",
                                                ),
                                            threshold = 0.7,
                                        ),
                                        RuleDefinitionRequest(
                                            name = "does not match",
                                            prompts =
                                                listOf(
                                                    "a maple tree",
                                                ),
                                            threshold = 0.7,
                                        ),
                                    ),
                            ),
                    ).shouldBeSuccessful()
                    .body

            response.results shouldHaveSize 2
            response.results.forExactly(1) {
                it.name shouldBe "matches"
                it.matched shouldBe true
                it.threshold shouldBe 0.7
                it.score shouldBeGreaterThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a joshua tree"
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "joshua tree national park"
                }
                it.promptScores shouldHaveSize 3
            }
            response.results.forExactly(1) {
                it.name shouldBe "does not match"
                it.matched shouldBe false
                it.threshold shouldBe 0.7
                it.score shouldBeLessThan 0.7
                it.promptScores.forAll { evaluatedPrompt ->
                    evaluatedPrompt.score shouldBeGreaterThan 0.0
                    evaluatedPrompt.score shouldBeLessThan 1.0
                }
                it.promptScores.forExactly(1) { evaluatedPrompt ->
                    evaluatedPrompt.prompt shouldBe "a maple tree"
                }
                it.promptScores shouldHaveSize 1
            }
        }
}
