package io.konifer.infrastructure.inference.embedding

interface RulePromptEmbeddingService {

    fun generateEmbeddings(prompt: String): FloatArray
}
