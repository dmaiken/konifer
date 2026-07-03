package io.konifer.infrastructure.rules.inference.embedding

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.ImageFactory
import io.konifer.TestImageType
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.rules.inference.Siglip2ModelFiles
import io.konifer.infrastructure.variant.ImageTensor
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.shouldNotBeNaN
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.foreign.Arena
import kotlin.io.path.pathString
import kotlin.math.sqrt

/**
 * Uses a per-class lifecycle because this class is expensive to build. It loads the vision model
 * into memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Siglip2ContentEmbeddingServiceTest {
    private val environment = OrtEnvironment.getEnvironment()
    private val ortSession =
        environment.createSession(Siglip2ModelFiles.visionModel().pathString, OrtSession.SessionOptions())
    private val tensorProcessor = VipsTensorProcessor()

    private lateinit var service: Siglip2ContentEmbeddingService

    @BeforeAll
    fun beforeAll() {
        service =
            Siglip2ContentEmbeddingService(
                ortEnvironment = environment,
                ortSession = ortSession,
            )
    }

    @Test
    fun `can generate embeddings`() =
        Vips.run { arena ->
            val tensor = createTensor(arena)
            // Ensure tensor is what we expect
            tensor.shape.contentEquals(longArrayOf(1, 3, 224, 224)) shouldBe true
            tensor.values.size shouldBe 1 * 3 * 224 * 224
            tensor.values.forEach { it.shouldNotBeNaN() }

            val embeddings = service.generateEmbeddings(tensor)

            embeddings shouldHaveAtLeastSize 1
            embeddings.forEach { it.shouldNotBeNaN() }
            embeddings shouldHaveSize 768

            val norm = sqrt(embeddings.sumOf { (it * it).toDouble() })
            norm shouldBe (1.0 plusOrMinus 0.0001)
        }

    @Test
    fun `returns stable embeddings for same tensors`() =
        Vips.run { arena ->
            val tensor = createTensor(arena)
            val first = service.generateEmbeddings(tensor)
            val second = service.generateEmbeddings(tensor)

            first.contentEquals(second) shouldBe true
        }

    @Test
    fun `returns different embeddings for different tensors`() =
        Vips.run { arena ->
            val tensor = createTensor(arena)
            val differentTensor = createTensor(arena, format = ImageFormat.PNG, type = TestImageType.MOON)
            val first = service.generateEmbeddings(tensor)
            val second = service.generateEmbeddings(differentTensor)

            first.contentEquals(second) shouldBe false
        }

    private fun createTensor(
        arena: Arena,
        format: ImageFormat = ImageFormat.PNG,
        type: TestImageType = TestImageType.JOSHUA_TREE,
    ): ImageTensor {
        val image = ImageFactory.testImage(format = format, type = type)
        return tensorProcessor.process(
            arena = arena,
            source = VImage.newFromBytes(arena, image.bytes),
            transformation = Siglip2TensorTransformation,
        )
    }
}
