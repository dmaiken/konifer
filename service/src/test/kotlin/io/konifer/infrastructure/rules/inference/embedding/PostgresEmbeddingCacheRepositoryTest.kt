package io.konifer.infrastructure.rules.inference.embedding

import io.konifer.infrastructure.datastore.postgres.PostgresContainerizedTest
import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresEmbeddingCacheRepositoryTest : PostgresContainerizedTest() {
    private companion object {
        const val DOG_PROMPT = "this is a photo of a dog"
        const val CAT_PROMPT = "this is a photo of a cat"
    }

    private val repository by lazy {
        PostgresEmbeddingCacheRepository(dslContext)
    }

    @Test
    fun `fetch all returns empty map when no embeddings have been cached`() {
        runTest {
            repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT) shouldBe emptyMap()
        }
    }

    @Test
    fun `stores and fetches cached embeddings`() {
        runTest {
            val dogEmbedding = floatArrayOf(0.1f, -0.2f, 0.3f)
            val catEmbedding = floatArrayOf(0.4f, 0.5f, -0.6f)

            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = DOG_PROMPT,
                embeddings = dogEmbedding,
            )
            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = CAT_PROMPT,
                embeddings = catEmbedding,
            )

            val embeddings = repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT, CAT_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly dogEmbedding.toList()
            embeddings.getValue(CAT_PROMPT).toList() shouldContainExactly catEmbedding.toList()
        }
    }

    @Test
    fun `fetch all only returns embeddings for requested model`() {
        runTest {
            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = DOG_PROMPT,
                embeddings = floatArrayOf(0.1f, 0.2f),
            )
            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_VISION,
                prompt = CAT_PROMPT,
                embeddings = floatArrayOf(0.3f, 0.4f),
            )

            val embeddings = repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly listOf(0.1f, 0.2f)
        }
    }

    @Test
    fun `store ignores duplicate prompt for same model`() {
        runTest {
            val originalEmbedding = floatArrayOf(0.1f, 0.2f)
            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = DOG_PROMPT,
                embeddings = originalEmbedding,
            )

            repository.store(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompt = DOG_PROMPT,
                embeddings = floatArrayOf(0.3f, 0.4f),
            )

            val embeddings = repository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly originalEmbedding.toList()
        }
    }
}
