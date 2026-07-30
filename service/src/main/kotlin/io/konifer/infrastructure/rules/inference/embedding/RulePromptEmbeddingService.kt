package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.domain.rules.RulePrompt

interface RulePromptEmbeddingService {
    /**
     * Generate embeddings for [prompts].
     */
    fun generateEmbeddings(prompts: List<RulePrompt>): Map<RulePrompt, FloatArray>
}
