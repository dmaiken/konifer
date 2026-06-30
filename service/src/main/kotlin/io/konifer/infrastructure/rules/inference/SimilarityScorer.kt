package io.konifer.infrastructure.rules.inference

import kotlin.math.exp

interface SimilarityScorer {
    fun score(
        promptEmbedding: FloatArray,
        contentEmbedding: FloatArray,
    ): Double
}

class Siglip2LogitSimilarityScorer(
    private val logitScale: Double = 0.0,
    private val logitBias: Double = 0.0,
) : SimilarityScorer {
    override fun score(
        promptEmbedding: FloatArray,
        contentEmbedding: FloatArray,
    ): Double {
        val cosineSimilarity = promptEmbedding dot contentEmbedding
        val logit = cosineSimilarity * exp(logitScale) + logitBias
        return sigmoid(logit)
    }

    private fun sigmoid(value: Double): Double = 1.0 / (1.0 + exp(-value))
}
