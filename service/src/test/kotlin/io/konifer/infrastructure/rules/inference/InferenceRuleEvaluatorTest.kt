package io.konifer.infrastructure.rules.inference

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RulePrompt
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.vips.processor.ImageTensor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class InferenceRuleEvaluatorTest {
    private val rulePromptEmbeddingService = mockk<RulePromptEmbeddingService>()
    private val contentEmbeddingService = mockk<ContentEmbeddingService>()
    private val similarityScorer = mockk<SimilarityScorer>()
    private val evaluator =
        InferenceRuleEvaluator(
            rulePromptEmbeddingService = rulePromptEmbeddingService,
            contentEmbeddingService = contentEmbeddingService,
            similarityScorer = similarityScorer,
        )

    @Test
    fun `empty rule definitions return empty result without generating embeddings`() =
        runTest {
            val result =
                evaluator.evaluate(
                    ruleDefinitions = emptyList(),
                    tensor = tensor,
                )

            result.results shouldBe emptyList()
            verify(exactly = 0) { contentEmbeddingService.generateEmbeddings(any()) }
            coVerify(exactly = 0) { rulePromptEmbeddingService.generateEmbeddings(any<List<RulePrompt>>()) }
        }

    @Test
    fun `generates content embedding once and evaluates all rule definitions`() =
        runTest {
            val matchingRule = ruleDefinition(prompts = listOf("dog"), threshold = 0.80)
            val nonMatchingRule = ruleDefinition(prompts = listOf("cat"), threshold = 0.90)

            val contentEmbedding = floatArrayOf(1.0f, 0.0f)
            every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
            val dogEmbedding = floatArrayOf(0.9f, 0.0f)
            val catEmbedding = floatArrayOf(0.7f, 0.0f)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(matchingRule.prompts) } returns
                promptEmbeddings(matchingRule.prompts, dogEmbedding)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(nonMatchingRule.prompts) } returns
                promptEmbeddings(nonMatchingRule.prompts, catEmbedding)
            every {
                similarityScorer.score(
                    promptEmbedding = dogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.9
            every {
                similarityScorer.score(
                    promptEmbedding = catEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.7

            val result =
                evaluator.evaluate(
                    ruleDefinitions = listOf(matchingRule, nonMatchingRule),
                    tensor = tensor,
                )

            result.results shouldHaveSize 2

            result.results[0].ruleDefinition shouldBe matchingRule
            result.results[0].evaluationScore.score shouldBe (0.9 plusOrMinus 0.000001)
            result.results[0].evaluationScore.matched shouldBe true

            result.results[1].ruleDefinition shouldBe nonMatchingRule
            result.results[1].evaluationScore.score shouldBe (0.7 plusOrMinus 0.000001)
            result.results[1].evaluationScore.matched shouldBe false

            verify(exactly = 1) { contentEmbeddingService.generateEmbeddings(tensor) }
            coVerify(exactly = 1) { rulePromptEmbeddingService.generateEmbeddings(matchingRule.prompts) }
            coVerify(exactly = 1) { rulePromptEmbeddingService.generateEmbeddings(nonMatchingRule.prompts) }
            verify(exactly = 1) { similarityScorer.score(dogEmbedding, contentEmbedding) }
            verify(exactly = 1) { similarityScorer.score(catEmbedding, contentEmbedding) }
        }

    @Test
    fun `rule matches when score equals threshold`() =
        runTest {
            val rule = ruleDefinition(prompts = listOf("dog"), threshold = 0.8)

            val contentEmbedding = floatArrayOf(1.0f, 0.0f)
            every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
            val dogEmbedding = floatArrayOf(0.8f, 0.0f)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(rule.prompts) } returns promptEmbeddings(rule.prompts, dogEmbedding)
            every {
                similarityScorer.score(
                    promptEmbedding = dogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.8

            val result =
                evaluator.evaluate(
                    ruleDefinitions = listOf(rule),
                    tensor = tensor,
                )

            result.results
                .single()
                .evaluationScore.score shouldBe (0.8 plusOrMinus 0.000001)
            result.results
                .single()
                .evaluationScore.matched shouldBe true
        }

    @Test
    fun `takes the max similarity from multiple prompts in definition`() =
        runTest {
            val rule = ruleDefinition(prompts = listOf("dog", "cat", "snake"), threshold = 0.8)

            val contentEmbedding = floatArrayOf(1.0f, 0.0f)
            every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
            val dogEmbedding = floatArrayOf(0.7f, 0.0f)
            val catEmbedding = floatArrayOf(0.8f, 0.0f)
            val snakeEmbedding = floatArrayOf(0.5f, 0.0f)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(rule.prompts) } returns
                promptEmbeddings(rule.prompts, dogEmbedding, catEmbedding, snakeEmbedding)
            every {
                similarityScorer.score(
                    promptEmbedding = dogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.7
            every {
                similarityScorer.score(
                    promptEmbedding = catEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.8
            every {
                similarityScorer.score(
                    promptEmbedding = snakeEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.5

            val result =
                evaluator.evaluate(
                    ruleDefinitions = listOf(rule),
                    tensor = tensor,
                )

            result.results
                .single()
                .evaluationScore.score shouldBe (0.8 plusOrMinus 0.000001)
            result.results
                .single()
                .evaluationScore.matched shouldBe true
        }

    @Test
    fun `throws when embeddings have different dimensions`() =
        runTest {
            val rule = ruleDefinition(prompts = listOf("dog"), threshold = 0.8)

            val contentEmbedding = floatArrayOf(1.0f, 0.0f)
            every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
            val dogEmbedding = floatArrayOf(1.0f)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(rule.prompts) } returns promptEmbeddings(rule.prompts, dogEmbedding)
            every {
                similarityScorer.score(
                    promptEmbedding = dogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } throws IllegalArgumentException("Cannot compute score for vectors of different sizes")

            shouldThrow<IllegalArgumentException> {
                evaluator.evaluate(
                    ruleDefinitions = listOf(rule),
                    tensor = tensor,
                )
            }
        }

    @Test
    fun `regardless of match prompt scores are returned`() =
        runTest {
            val matchingRule = ruleDefinition(prompts = listOf("dog", "nice dog"), threshold = 0.80)
            val nonMatchingRule = ruleDefinition(prompts = listOf("cat", "nice cat"), threshold = 0.90)

            val contentEmbedding = floatArrayOf(1.0f, 0.0f)
            every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
            val dogEmbedding = floatArrayOf(0.9f, 0.0f)
            val catEmbedding = floatArrayOf(0.7f, 0.0f)
            val niceDogEmbedding = floatArrayOf(0.4f, 0.0f)
            val niceCatEmbedding = floatArrayOf(0.3f, 0.0f)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(matchingRule.prompts) } returns
                promptEmbeddings(matchingRule.prompts, dogEmbedding, niceDogEmbedding)
            coEvery { rulePromptEmbeddingService.generateEmbeddings(nonMatchingRule.prompts) } returns
                promptEmbeddings(nonMatchingRule.prompts, catEmbedding, niceCatEmbedding)
            every {
                similarityScorer.score(
                    promptEmbedding = dogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.9
            every {
                similarityScorer.score(
                    promptEmbedding = catEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.7
            every {
                similarityScorer.score(
                    promptEmbedding = niceDogEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.4
            every {
                similarityScorer.score(
                    promptEmbedding = niceCatEmbedding,
                    contentEmbedding = contentEmbedding,
                )
            } returns 0.3

            val result =
                evaluator.evaluate(
                    ruleDefinitions = listOf(matchingRule, nonMatchingRule),
                    tensor = tensor,
                )

            result.results shouldHaveSize 2

            result.results[0].ruleDefinition shouldBe matchingRule
            result.results[0].evaluationScore.score shouldBe (0.9 plusOrMinus 0.000001)
            result.results[0].evaluationScore.matched shouldBe true
            result.results[0].promptScores shouldContainExactly
                mapOf(
                    RulePrompt("dog") to 0.9,
                    RulePrompt("nice dog") to 0.4,
                )

            result.results[1].ruleDefinition shouldBe nonMatchingRule
            result.results[1].evaluationScore.score shouldBe (0.7 plusOrMinus 0.000001)
            result.results[1].promptScores shouldContainExactly
                mapOf(
                    RulePrompt("cat") to 0.7,
                    RulePrompt("nice cat") to 0.3,
                )
            result.results[1].evaluationScore.matched shouldBe false
        }

    private fun promptEmbeddings(
        prompts: List<RulePrompt>,
        vararg embeddings: FloatArray,
    ): Map<RulePrompt, FloatArray> = prompts.zip(embeddings).toMap()

    private fun ruleDefinition(
        prompts: List<String>,
        threshold: Double,
    ): RuleDefinition =
        RuleDefinition(
            name = RuleName(prompts.first()),
            prompts = prompts.map { RulePrompt(it) },
            threshold = RuleDefinitionThreshold(threshold),
        )

    private companion object {
        val tensor =
            ImageTensor(
                values = floatArrayOf(1.0f, 0.0f),
                shape = longArrayOf(1, 2),
            )
    }
}
