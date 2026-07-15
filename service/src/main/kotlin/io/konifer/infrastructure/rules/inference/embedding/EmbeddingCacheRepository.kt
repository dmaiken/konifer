package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel

interface EmbeddingCacheRepository {
    suspend fun fetchAll(model: EmbeddingModel): Map<String, FloatArray>

    suspend fun store(
        model: EmbeddingModel,
        prompt: String,
        embeddings: FloatArray,
    )
}
