package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.ktor.util.logging.KtorSimpleLogger

class NoOpEmbeddingCacheRepository : EmbeddingCacheRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        logger.warn("Using NoOpEmbeddingCacheRepository. Prompts will NOT be cached!")
    }

    override suspend fun fetchAll(model: EmbeddingModel): Map<String, FloatArray> = emptyMap()

    override suspend fun store(
        model: EmbeddingModel,
        prompt: String,
        embeddings: FloatArray,
    ) {
        // No-op
    }
}
