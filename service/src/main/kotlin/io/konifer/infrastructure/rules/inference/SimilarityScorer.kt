package io.konifer.infrastructure.rules.inference

import kotlin.math.exp

interface SimilarityScorer {
    fun score(
        promptEmbedding: FloatArray,
        contentEmbedding: FloatArray,
    ): Double
}

class Siglip2LogitSimilarityScorer : SimilarityScorer {
    companion object {
        private const val LOGIT_SCALE = 4.724453449249268
        private const val LOGIT_BIAS = -16.771724700927734
    }

    override fun score(
        promptEmbedding: FloatArray,
        contentEmbedding: FloatArray,
    ): Double {
        val cosineSimilarity = promptEmbedding dot contentEmbedding
        val logit = cosineSimilarity * exp(LOGIT_SCALE) + LOGIT_BIAS
        return sigmoid(logit)
    }

    private fun sigmoid(value: Double): Double = 1.0 / (1.0 + exp(-value))
}
