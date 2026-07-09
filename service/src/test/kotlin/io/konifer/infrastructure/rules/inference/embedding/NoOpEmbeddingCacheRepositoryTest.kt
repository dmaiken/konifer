package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.Model
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NoOpEmbeddingCacheRepositoryTest {
    private val repository = NoOpEmbeddingCacheRepository()

    @Test
    fun `fetch all returns empty map`() {
        runTest {
            repository.fetchAll(Model.SIGLIP2_TEXT) shouldBe emptyMap()
        }
    }

    @Test
    fun `store does not persist embeddings`() {
        runTest {
            repository.store(
                model = Model.SIGLIP2_TEXT,
                prompt = "this is a photo of a dog",
                embeddings = floatArrayOf(0.1f, 0.2f),
            )

            repository.fetchAll(Model.SIGLIP2_TEXT) shouldBe emptyMap()
        }
    }
}
