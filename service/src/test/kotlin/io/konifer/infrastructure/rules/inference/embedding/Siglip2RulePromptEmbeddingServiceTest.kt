package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.RuleDefinitionThreshold
import io.konifer.infrastructure.rules.inference.Siglip2Tokenizer
import io.konifer.infrastructure.rules.inference.embedding.Siglip2RulePromptEmbeddingService
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldNotBeNaN
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.math.sqrt

/**
 * Uses a per-class lifecycle because this class is expensive to build. It loads the text model
 * into memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Siglip2RulePromptEmbeddingServiceTest {
    private lateinit var tokenizer: Siglip2Tokenizer
    private lateinit var service: Siglip2RulePromptEmbeddingService

    private val prompt1 = "This is a photo of a test prompt"
    private val prompt2 = "another test prompt"
    private val incorrectFormatPrompt = "   A TEST PROMPT  "
    private val ruleDefinitions =
        listOf(
            RuleDefinition(
                prompts = listOf(prompt1),
                threshold = RuleDefinitionThreshold(0.5),
            ),
            RuleDefinition(
                prompts = listOf(prompt2),
                threshold = RuleDefinitionThreshold(0.9),
            ),
        )
    private val environment = OrtEnvironment.getEnvironment()
    private val modelPath = Path.of("/home/daniel/imagek/text_model.onnx")
    private val ortSession = environment.createSession(modelPath.pathString, OrtSession.SessionOptions())

    @BeforeAll
    fun beforeAll() {
        tokenizer = spyk(Siglip2Tokenizer())

        service =
            Siglip2RulePromptEmbeddingService(
                tokenizer = tokenizer,
                ortEnvironment = environment,
                ortSession = ortSession,
                ruleDefinitions = ruleDefinitions,
            )
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
}
