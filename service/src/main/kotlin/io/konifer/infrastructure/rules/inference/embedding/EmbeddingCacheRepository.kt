package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.Model

interface EmbeddingCacheRepository {
    suspend fun fetchAll(model: Model): Map<String, FloatArray>

    suspend fun store(
        model: Model,
        prompt: String,
        embeddings: FloatArray,
    )
}
