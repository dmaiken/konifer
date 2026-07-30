package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class InMemoryEmbeddingCacheRepository : EmbeddingCacheRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val embeddingsByModel = ConcurrentHashMap<EmbeddingModel, MutableMap<String, FloatArray>>()
    private val storeMutex = Mutex()

    init {
        logger.warn("The in-memory embedding cache is enabled. This should NOT be used in production!")
    }

    override suspend fun fetch(
        prompts: List<String>,
        embeddingModel: EmbeddingModel,
    ): Map<String, FloatArray> =
        embeddingsByModel[embeddingModel]
            ?.filterKeys { it in prompts.toSet() }
            ?.mapValues { (_, embedding) -> embedding.copyOf() }
            ?: emptyMap()

    override suspend fun storeAll(
        embeddingModel: EmbeddingModel,
        prompts: Map<String, FloatArray>,
    ) {
        if (prompts.isEmpty()) return

        storeMutex.withLock {
            val embeddings =
                embeddingsByModel.computeIfAbsent(embeddingModel) {
                    ConcurrentHashMap()
                }
            prompts.forEach { (prompt, embedding) ->
                embeddings.putIfAbsent(prompt, embedding.copyOf())
            }
        }
    }
}
