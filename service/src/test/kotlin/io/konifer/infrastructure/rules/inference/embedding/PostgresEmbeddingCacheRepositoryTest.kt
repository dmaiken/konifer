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
        const val BIRD_PROMPT = "this is a photo of a bird"
    }

    private val repository by lazy {
        PostgresEmbeddingCacheRepository(dslContext)
    }

    @Test
    fun `fetch returns empty map when no requested embeddings have been cached`() {
        runTest {
            repository.fetch(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = listOf(DOG_PROMPT),
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
                        DOG_PROMPT to dogEmbedding,
                        CAT_PROMPT to catEmbedding,
                    ),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf(DOG_PROMPT, CAT_PROMPT, BIRD_PROMPT),
                )

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT, CAT_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly dogEmbedding.toList()
            embeddings.getValue(CAT_PROMPT).toList() shouldContainExactly catEmbedding.toList()
        }
    }

    @Test
    fun `fetch only returns requested prompts`() {
        runTest {
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts =
                    mapOf(
                        DOG_PROMPT to floatArrayOf(0.1f, 0.2f),
                        CAT_PROMPT to floatArrayOf(0.3f, 0.4f),
                    ),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf(DOG_PROMPT),
                )

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly listOf(0.1f, 0.2f)
        }
    }

    @Test
    fun `fetch only returns embeddings for requested model`() {
        runTest {
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf(DOG_PROMPT to floatArrayOf(0.1f, 0.2f)),
            )
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_VISION,
                prompts = mapOf(CAT_PROMPT to floatArrayOf(0.3f, 0.4f)),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf(DOG_PROMPT, CAT_PROMPT),
                )

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly listOf(0.1f, 0.2f)
        }
    }

    @Test
    fun `storeAll ignores duplicate prompts for same model`() {
        runTest {
            val originalEmbedding = floatArrayOf(0.1f, 0.2f)
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf(DOG_PROMPT to originalEmbedding),
            )

            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = mapOf(DOG_PROMPT to floatArrayOf(0.3f, 0.4f)),
            )

            val embeddings =
                repository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf(DOG_PROMPT),
                )

            embeddings.keys shouldContainExactly setOf(DOG_PROMPT)
            embeddings.getValue(DOG_PROMPT).toList() shouldContainExactly originalEmbedding.toList()
        }
    }

    @Test
    fun `storeAll with empty prompts does nothing`() {
        runTest {
            repository.storeAll(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = emptyMap(),
            )

            repository.fetch(
                embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                prompts = listOf(DOG_PROMPT),
            ) shouldBe emptyMap()
        }
    }
}
