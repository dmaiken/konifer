package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class InMemoryEmbeddingCacheRepositoryTest {
    private val repository = InMemoryEmbeddingCacheRepository()

    @Test
    fun `fetch returns empty map when no embeddings have been stored`() {
        runTest {
            repository.fetch(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = listOf("this is a photo of a dog"),
            ) shouldBe emptyMap()
        }
    }

    @Test
    fun `storeAll stores and fetch returns requested cached embeddings`() {
        runTest {
            val dogEmbedding = floatArrayOf(0.1f, -0.2f, 0.3f)
            val catEmbedding = floatArrayOf(0.4f, 0.5f, -0.6f)

            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts =
                    mapOf(
                        "this is a photo of a dog" to dogEmbedding,
                        "this is a photo of a cat" to catEmbedding,
                    ),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf("this is a photo of a dog"),
                )

            embeddings.keys shouldContainExactly setOf("this is a photo of a dog")
            embeddings.getValue("this is a photo of a dog").toList() shouldContainExactly dogEmbedding.toList()
        }
    }

    @Test
    fun `fetch only returns embeddings for requested model`() {
        runTest {
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf("this is a photo of a dog" to floatArrayOf(0.1f, 0.2f)),
            )
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_VISION,
                prompts = mapOf("this is a photo of a cat" to floatArrayOf(0.3f, 0.4f)),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf("this is a photo of a dog", "this is a photo of a cat"),
                )

            embeddings.keys shouldContainExactly setOf("this is a photo of a dog")
            embeddings.getValue("this is a photo of a dog").toList() shouldContainExactly listOf(0.1f, 0.2f)
        }
    }

    @Test
    fun `storeAll ignores duplicate prompts for same model`() {
        runTest {
            val originalEmbedding = floatArrayOf(0.1f, 0.2f)
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf("this is a photo of a dog" to originalEmbedding),
            )

            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf("this is a photo of a dog" to floatArrayOf(0.3f, 0.4f)),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf("this is a photo of a dog"),
                )

            embeddings.keys shouldContainExactly setOf("this is a photo of a dog")
            embeddings.getValue("this is a photo of a dog").toList() shouldContainExactly originalEmbedding.toList()
        }
    }

    @Test
    fun `fetch returns copies of stored embeddings`() {
        runTest {
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf("this is a photo of a dog" to floatArrayOf(0.1f, 0.2f)),
            )

            val first =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf("this is a photo of a dog"),
                )
            first.getValue("this is a photo of a dog")[0] = 9.9f

            val second =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf("this is a photo of a dog"),
                )

            second.getValue("this is a photo of a dog").toList() shouldContainExactly listOf(0.1f, 0.2f)
        }
    }
}
