package io.konifer.infrastructure.rules.inference.embedding

interface RulePromptEmbeddingService {
    fun generateEmbeddings(prompt: String): FloatArray
}
