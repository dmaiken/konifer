package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel

interface EmbeddingCacheRepository {
    suspend fun fetch(
        prompts: List<String>,
        embeddingModel: EmbeddingModel,
    ): Map<String, FloatArray>

    suspend fun storeAll(
        embeddingModel: EmbeddingModel,
        prompts: Map<String, FloatArray>,
    )
}
