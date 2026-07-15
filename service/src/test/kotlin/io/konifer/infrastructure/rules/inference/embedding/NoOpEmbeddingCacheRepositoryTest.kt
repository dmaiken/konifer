package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NoOpEmbeddingCacheRepositoryTest {
    private val repository = NoOpEmbeddingCacheRepository()

    @Test
    fun `fetch all returns empty map`() {
        runTest {
            repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT) shouldBe emptyMap()
        }
    }

    @Test
    fun `store does not persist embeddings`() {
        runTest {
            repository.store(
                model = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = "this is a photo of a dog",
                embeddings = floatArrayOf(0.1f, 0.2f),
            )

            repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT) shouldBe emptyMap()
        }
    }
}
