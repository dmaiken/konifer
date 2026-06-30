package io.konifer.infrastructure.inference

import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.infrastructure.rules.inference.InferenceRuleEvaluator
import io.konifer.infrastructure.rules.inference.SimilarityScorer
import io.konifer.infrastructure.rules.inference.embedding.ContentEmbeddingService
import io.konifer.infrastructure.rules.inference.embedding.RulePromptEmbeddingService
import io.konifer.infrastructure.variant.ImageTensor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    fun `empty rule definitions return empty result without generating embeddings`() {
        val result =
            evaluator.evaluate(
                ruleDefinitions = emptyList(),
                tensor = tensor,
            )

        result.results shouldBe emptyList()
        verify(exactly = 0) { contentEmbeddingService.generateEmbeddings(any()) }
        verify(exactly = 0) { rulePromptEmbeddingService.generateEmbeddings(any()) }
    }

    @Test
    fun `generates content embedding once and evaluates all rule definitions`() {
        val matchingRule = ruleDefinition(prompt = "dog", threshold = 0.80)
        val nonMatchingRule = ruleDefinition(prompt = "cat", threshold = 0.90)

        val contentEmbedding = floatArrayOf(1.0f, 0.0f)
        every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
        val dogEmbedding = floatArrayOf(0.9f, 0.0f)
        val catEmbedding = floatArrayOf(0.7f, 0.0f)
        every { rulePromptEmbeddingService.generateEmbeddings("dog") } returns dogEmbedding
        every { rulePromptEmbeddingService.generateEmbeddings("cat") } returns catEmbedding
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
        result.results[0].score shouldBe (0.9 plusOrMinus 0.000001)
        result.results[0].matched shouldBe true

        result.results[1].ruleDefinition shouldBe nonMatchingRule
        result.results[1].score shouldBe (0.7 plusOrMinus 0.000001)
        result.results[1].matched shouldBe false

        verify(exactly = 1) { contentEmbeddingService.generateEmbeddings(tensor) }
        verify(exactly = 1) { rulePromptEmbeddingService.generateEmbeddings("dog") }
        verify(exactly = 1) { rulePromptEmbeddingService.generateEmbeddings("cat") }
        verify(exactly = 1) { similarityScorer.score(dogEmbedding, contentEmbedding) }
        verify(exactly = 1) { similarityScorer.score(catEmbedding, contentEmbedding) }
    }

    @Test
    fun `rule matches when score equals threshold`() {
        val rule = ruleDefinition(prompt = "dog", threshold = 0.8)

        val contentEmbedding = floatArrayOf(1.0f, 0.0f)
        every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
        val dogEmbedding = floatArrayOf(0.8f, 0.0f)
        every { rulePromptEmbeddingService.generateEmbeddings("dog") } returns dogEmbedding
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

        result.results.single().score shouldBe (0.8 plusOrMinus 0.000001)
        result.results.single().matched shouldBe true
    }

    @Test
    fun `throws when embeddings have different dimensions`() {
        val rule = ruleDefinition(prompt = "dog", threshold = 0.8)

        val contentEmbedding = floatArrayOf(1.0f, 0.0f)
        every { contentEmbeddingService.generateEmbeddings(tensor) } returns contentEmbedding
        val dogEmbedding = floatArrayOf(1.0f)
        every { rulePromptEmbeddingService.generateEmbeddings("dog") } returns dogEmbedding
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

    private fun ruleDefinition(
        prompt: String,
        threshold: Double,
    ): RuleDefinition =
        RuleDefinition(
            prompt = prompt,
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
