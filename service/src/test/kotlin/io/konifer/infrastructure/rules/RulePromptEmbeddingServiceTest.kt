package io.konifer.infrastructure.rules

import ai.onnxruntime.OrtEnvironment
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldNotBeNaN
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import java.util.UUID
import kotlin.math.sqrt

/**
 * Uses a per-class lifecycle because this class is expensive to build. It loads the text model
 * into memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RulePromptEmbeddingServiceTest {
    private lateinit var tokenizer: Siglip2Tokenizer
    private lateinit var service: RulePromptEmbeddingService

    private val environment = OrtEnvironment.getEnvironment()
    private val modelPath = Path.of("/home/daniel/imagek/text_model.onnx")

    @BeforeAll
    fun beforeAll() {
        tokenizer = spyk(Siglip2Tokenizer())
        service =
            RulePromptEmbeddingService(
                tokenizer = tokenizer,
                ortEnvironment = environment,
                pathToModel = modelPath,
            )
    }

    @Test
    fun `can generate embeddings`() {
        val prompt = UUID.randomUUID().toString()
        val embeddings = service.generateEmbeddings(prompt)

        embeddings shouldHaveAtLeastSize 1
        embeddings.forEach { it.shouldNotBeNaN() }

        val norm = sqrt(embeddings.sumOf { (it * it).toDouble() })
        norm shouldBe (1.0 plusOrMinus 0.0001)
    }

    @Test
    fun `returns stable embeddings for same prompt`() {
        val prompt = UUID.randomUUID().toString()
        val first = service.generateEmbeddings(prompt)
        val second = service.generateEmbeddings(prompt)

        first.contentEquals(second) shouldBe true
    }

    @Test
    fun `embedding is cached the first time`() {
        val prompt = UUID.randomUUID().toString()
        service.generateEmbeddings(prompt)
        verify(exactly = 1) { tokenizer.encode(prompt) }

        clearMocks(tokenizer)

        service.generateEmbeddings(prompt)
        verify(exactly = 0) { tokenizer.encode(prompt) }
    }
}
