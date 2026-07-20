package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OrtEnvironment
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.domain.rules.RuleName
import io.konifer.infrastructure.rules.inference.EmbeddingModel
import io.konifer.infrastructure.rules.inference.OnnxSessionFactory
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldNotBeNaN
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
                prompts = listOf(prompt1),
                threshold = RuleDefinitionThreshold(0.5),
            ),
            RuleDefinition(
                name = RuleName("another-test-prompt"),
                prompts = listOf(prompt2),
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
        val embeddings = service.generateEmbeddings(prompt1)

        embeddings shouldHaveAtLeastSize 1
        embeddings.forEach { it.shouldNotBeNaN() }

        val norm = sqrt(embeddings.sumOf { (it * it).toDouble() })
        norm shouldBe (1.0 plusOrMinus 0.0001)
    }

    @Test
    fun `returns stable embeddings for same prompt`() {
        val first = service.generateEmbeddings(prompt1)
        val second = service.generateEmbeddings(prompt1)

        first.contentEquals(second) shouldBe true
    }

    @Test
    fun `returns different embeddings for different prompts`() {
        val first = service.generateEmbeddings(prompt1)
        val second = service.generateEmbeddings(prompt2)

        first.contentEquals(second) shouldBe false
    }

    @Test
    fun `preprocesses text before generating embeddings`() {
        val first = service.generateEmbeddings(incorrectFormatPrompt)
        val second = service.generateEmbeddings(prompt1)

        first.contentEquals(second) shouldBe true
    }

    @Test
    fun `stores generated prompt embeddings in cache`() =
        runTest {
            service.generateEmbeddings(prompt1)

            val cached = embeddingCacheRepository.fetchAll(EmbeddingModel.SIGLIP2_BASE_PATCH16_224_TEXT)

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
                            prompts = listOf(prompt1),
                            threshold = RuleDefinitionThreshold(0.5),
                        ),
                    ),
                embeddingCacheRepository = cachedRepository,
                dispatcher = Dispatchers.Default,
                tokenizerFactory = ::Siglip2Tokenizer,
            )

        cachedService.use { cachedService ->
            val embedding = cachedService.generateEmbeddings(prompt1)

            embedding.contentEquals(cachedEmbedding) shouldBe true
            cachedRepository.storeCalls shouldBe emptyMap()
        }
    }

    private class RecordingEmbeddingCacheRepository(
        initialEmbeddings: Map<String, FloatArray> = emptyMap(),
    ) : EmbeddingCacheRepository {
        private val embeddings = initialEmbeddings.toMutableMap()
        private val stores = mutableMapOf<String, FloatArray>()

        val storeCalls: Map<String, FloatArray>
            get() = stores.toMap()

        override suspend fun fetchAll(embeddingModel: EmbeddingModel): Map<String, FloatArray> = embeddings.toMap()

        override suspend fun store(
            embeddingModel: EmbeddingModel,
            prompt: String,
            embeddings: FloatArray,
        ) {
            stores[prompt] = embeddings
            this.embeddings[prompt] = embeddings
        }
    }
}
