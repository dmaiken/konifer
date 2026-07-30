package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OrtEnvironment
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.konifer.domain.rules.RulePrompt
import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldNotBeNaN
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.math.sqrt

/**
 * Uses a per-class lifecycle because this class is expensive to build. It loads the text model
 * into memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Siglip2RulePromptEmbeddingServiceTest {
    private lateinit var service: Siglip2RulePromptEmbeddingService

    private val prompt1 = "This is a photo of a test prompt"
    private val prompt2 = "another test prompt"
    private val incorrectFormatPrompt = "   A TEST PROMPT  "
    private val preprocessedPrompt1 = "this is a photo of a test prompt"
    private val preprocessedPrompt2 = "this is a photo of another test prompt"
    private val ruleDefinitions =
        listOf(
            RuleDefinition(
                name = RuleName("test-prompt"),
                prompts = listOf(RulePrompt(prompt1)),
                threshold = RuleDefinitionThreshold(0.5),
            ),
            RuleDefinition(
                name = RuleName("another-test-prompt"),
                prompts = listOf(RulePrompt(prompt2)),
                threshold = RuleDefinitionThreshold(0.9),
            ),
        )
    private val environment = OrtEnvironment.getEnvironment()
    private val embeddingCacheRepository = RecordingEmbeddingCacheRepository()

    @BeforeAll
    fun beforeAll() {
        service =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = OnnxSessionFactory(environment),
                ruleDefinitions = ruleDefinitions,
                embeddingCacheRepository = embeddingCacheRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )
    }

    @AfterAll
    fun afterAll() {
        service.close()
    }

    @Test
    fun `can generate embeddings`() {
        val embeddings = service.generateSingleEmbedding(prompt1)

        embeddings shouldHaveAtLeastSize 1
        embeddings.forEach { it.shouldNotBeNaN() }

        val norm = sqrt(embeddings.sumOf { (it * it).toDouble() })
        norm shouldBe (1.0 plusOrMinus 0.0001)
    }

    @Test
    fun `returns stable embeddings for same prompt`() {
        val first = service.generateSingleEmbedding(prompt1)
        val second = service.generateSingleEmbedding(prompt1)

        first.contentEquals(second) shouldBe true
    }

    @Test
    fun `returns different embeddings for different prompts`() {
        val first = service.generateSingleEmbedding(prompt1)
        val second = service.generateSingleEmbedding(prompt2)

        first.contentEquals(second) shouldBe false
    }

    @Test
    fun `can generate embeddings for multiple prompts`() {
        val prompts = rulePrompts(prompt1, prompt2, incorrectFormatPrompt)
        val embeddings = service.generateEmbeddings(prompts)

        embeddings.size shouldBe 3
        embeddings.getValue(prompts[0]).contentEquals(embeddings.getValue(prompts[1])) shouldBe false
        embeddings.getValue(prompts[0]).contentEquals(embeddings.getValue(prompts[2])) shouldBe true
        embeddings.values.forEach { embedding ->
            embedding shouldHaveAtLeastSize 1
            embedding.forEach { it.shouldNotBeNaN() }
        }
    }

    @Test
    fun `preprocesses text before generating embeddings`() {
        val first = service.generateSingleEmbedding(incorrectFormatPrompt)
        val second = service.generateSingleEmbedding(prompt1)

        first.contentEquals(second) shouldBe true
    }

    @Test
    fun `stores generated prompt embeddings in cache`() =
        runTest {
            service.generateEmbeddings(rulePrompts(prompt1))

            val cached =
                embeddingCacheRepository.fetch(
                    embeddingModel = EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT,
                    prompts = listOf(preprocessedPrompt1, preprocessedPrompt2),
                )

            cached.keys shouldBe setOf(preprocessedPrompt1, preprocessedPrompt2)
            cached.getValue(preprocessedPrompt1).toList().shouldNotBeEmpty()
            cached.getValue(preprocessedPrompt2).toList().shouldNotBeEmpty()
        }

    @Test
    fun `uses cached prompt embeddings without creating text model session`() {
        val cachedEmbedding = floatArrayOf(0.25f, -0.5f, 0.75f)
        val cachedRepository =
            RecordingEmbeddingCacheRepository(
                initialEmbeddings = mapOf(preprocessedPrompt1 to cachedEmbedding),
            )
        val onnxSessionFactory =
            mockk<OnnxSessionFactory> {
                every { create(any()) } throws AssertionError("Text model session should not be created")
            }
        val cachedService =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = onnxSessionFactory,
                ruleDefinitions =
                    listOf(
                        RuleDefinition(
                            name = RuleName("test-prompt"),
                            prompts = listOf(RulePrompt(prompt1)),
                            threshold = RuleDefinitionThreshold(0.5),
                        ),
                    ),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )

        cachedService.use { cachedService ->
            val embedding = cachedService.generateSingleEmbedding(prompt1)

            embedding.contentEquals(cachedEmbedding) shouldBe true
            cachedRepository.storeCalls shouldBe emptyMap()
        }
    }

    @Test
    fun `returns embeddings keyed by rule prompt while fetching cache by preprocessed prompt`() {
        val cachedEmbedding = floatArrayOf(0.25f, -0.5f, 0.75f)
        val cachedRepository =
            RecordingEmbeddingCacheRepository(
                initialEmbeddings = mapOf(preprocessedPrompt1 to cachedEmbedding),
            )
        val onnxSessionFactory =
            mockk<OnnxSessionFactory> {
                every { create(any()) } throws AssertionError("Text model session should not be created")
            }
        val cachedService =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = onnxSessionFactory,
                ruleDefinitions = emptyList(),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )
        val prompt = RulePrompt(prompt1)

        cachedService.use { cachedService ->
            val embeddings = cachedService.generateEmbeddings(listOf(prompt))

            embeddings shouldContainExactly mapOf(prompt to cachedEmbedding)
            cachedRepository.fetchCalls shouldBe listOf(listOf(preprocessedPrompt1))
        }
    }

    @Test
    fun `fetches distinct preprocessed prompts from cache`() {
        val cachedEmbedding = floatArrayOf(0.25f, -0.5f, 0.75f)
        val cachedRepository =
            RecordingEmbeddingCacheRepository(
                initialEmbeddings = mapOf(preprocessedPrompt1 to cachedEmbedding),
            )
        val onnxSessionFactory =
            mockk<OnnxSessionFactory> {
                every { create(any()) } throws AssertionError("Text model session should not be created")
            }
        val cachedService =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = onnxSessionFactory,
                ruleDefinitions = emptyList(),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )
        val prompts = rulePrompts(prompt1, incorrectFormatPrompt)

        cachedService.use { cachedService ->
            val embeddings = cachedService.generateEmbeddings(prompts)

            embeddings.size shouldBe 2
            embeddings.getValue(prompts[0]).contentEquals(cachedEmbedding) shouldBe true
            embeddings.getValue(prompts[1]).contentEquals(cachedEmbedding) shouldBe true
            cachedRepository.fetchCalls shouldBe listOf(listOf(preprocessedPrompt1))
        }
    }

    @Test
    fun `does not reuse stale in-memory prompt embeddings across calls`() {
        val firstEmbedding = floatArrayOf(0.25f, -0.5f, 0.75f)
        val secondEmbedding = floatArrayOf(-0.1f, 0.2f, -0.3f)
        val cachedRepository =
            RecordingEmbeddingCacheRepository(
                initialEmbeddings = mapOf(preprocessedPrompt1 to firstEmbedding),
            )
        val onnxSessionFactory =
            mockk<OnnxSessionFactory> {
                every { create(any()) } throws AssertionError("Text model session should not be created")
            }
        val cachedService =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = onnxSessionFactory,
                ruleDefinitions = emptyList(),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )

        cachedService.use { cachedService ->
            val first = cachedService.generateSingleEmbedding(prompt1)
            cachedRepository.replace(preprocessedPrompt1, secondEmbedding)
            val second = cachedService.generateSingleEmbedding(prompt1)

            first.contentEquals(firstEmbedding) shouldBe true
            second.contentEquals(secondEmbedding) shouldBe true
            cachedRepository.fetchCalls shouldBe
                listOf(
                    listOf(preprocessedPrompt1),
                    listOf(preprocessedPrompt1),
                )
        }
    }

    @Test
    fun `throws for uncached prompts when embedding cache misses are disabled`() {
        val cachedRepository = RecordingEmbeddingCacheRepository()
        val onnxSessionFactory =
            mockk<OnnxSessionFactory> {
                every { create(any()) } throws AssertionError("Text model session should not be created")
            }
        val cachedOnlyService =
            Siglip2RulePromptEmbeddingService(
                ortEnvironment = environment,
                onnxSessionFactory = onnxSessionFactory,
                ruleDefinitions = emptyList(),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
                allowEmbeddingCacheMiss = false,
            )

        cachedOnlyService.use { cachedOnlyService ->
            shouldThrow<IllegalArgumentException> {
                cachedOnlyService.generateEmbeddings(rulePrompts("uncached prompt"))
            }.message shouldBe "Embeddings for prompts were not configured: this is a photo of uncached prompt"

            cachedRepository.storeCalls shouldBe emptyMap()
        }
    }

    private fun Siglip2RulePromptEmbeddingService.generateSingleEmbedding(prompt: String): FloatArray =
        generateEmbeddings(rulePrompts(prompt)).values.single()

    private fun rulePrompts(vararg prompts: String): List<RulePrompt> = prompts.map { RulePrompt(it) }

    private class RecordingEmbeddingCacheRepository(
        initialEmbeddings: Map<String, FloatArray> = emptyMap(),
    ) : EmbeddingCacheRepository {
        private val embeddings = initialEmbeddings.toMutableMap()
        private val stores = mutableMapOf<String, FloatArray>()
        private val fetches = mutableListOf<List<String>>()

        val storeCalls: Map<String, FloatArray>
            get() = stores.toMap()

        val fetchCalls: List<List<String>>
            get() = fetches.toList()

        fun replace(
            prompt: String,
            embedding: FloatArray,
        ) {
            embeddings[prompt] = embedding
        }

        override suspend fun fetch(
            prompts: List<String>,
            embeddingModel: EmbeddingModel,
        ): Map<String, FloatArray> {
            fetches += prompts
            return embeddings.filterKeys { it in prompts }
        }

        override suspend fun storeAll(
            embeddingModel: EmbeddingModel,
            prompts: Map<String, FloatArray>,
        ) {
            stores.putAll(prompts)
            embeddings.putAll(prompts)
        }
    }
}
