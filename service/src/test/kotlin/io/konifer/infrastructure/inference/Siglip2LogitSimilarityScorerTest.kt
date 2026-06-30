package io.konifer.infrastructure.inference

import io.konifer.infrastructure.rules.inference.Siglip2LogitSimilarityScorer
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.exp

class Siglip2LogitSimilarityScorerTest {

    @Test
    fun `scores embeddings as sigmoid of scaled biased dot product`() {
        val scorer =
            Siglip2LogitSimilarityScorer(
                logitScale = 2.0,
                logitBias = -1.0,
            )

        val score =
            scorer.score(
                promptEmbedding = floatArrayOf(0.5f, 0.5f),
                contentEmbedding = floatArrayOf(1.0f, 0.0f),
            )

        val expectedLogit = 0.5 * exp(2.0) - 1.0
        val expectedScore = 1.0 / (1.0 + exp(-expectedLogit))
        score shouldBe (expectedScore plusOrMinus 0.000001)
    }
}
