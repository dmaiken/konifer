package io.konifer.infrastructure.vips.processor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.variant.Siglip2TensorTransformation
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.lang.foreign.Arena
import java.nio.ByteOrder
import java.nio.file.Path
import javax.imageio.ImageIO

class VipsTensorProcessorTest {
    private companion object {
        // Expected values from (2 * sample - 255) / 255, independently of the production calculation.
        const val NORMALIZED_32 = -191f / 255f
        const val NORMALIZED_64 = -127f / 255f
        const val NORMALIZED_128 = 1f / 255f
    }

    @TempDir
    lateinit var temporaryDirectory: Path

    private val processor = VipsTensorProcessor()

    @Test
    fun `ONNX receives normalized samples without consuming the reusable buffer`() {
        val tensor = process(writeImage(BufferedImage.TYPE_INT_RGB) { _, _ -> 0x004080ff })
        val planeSize = 224 * 224
        repeat(2) {
            OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), tensor.values, tensor.shape).use { onnxTensor ->
                onnxTensor.info.shape.toList() shouldBe listOf(1L, 3L, 224L, 224L)
                val values = onnxTensor.floatBuffer
                values.remaining() shouldBe 3 * planeSize
                values.get(0) shouldBe (NORMALIZED_64 plusOrMinus 0.000001f)
                values.get(planeSize) shouldBe (NORMALIZED_128 plusOrMinus 0.000001f)
                values.get(2 * planeSize) shouldBe 1f
            }
            assertBufferLayout(tensor)
        }
    }

    @Test
    fun `normalizes unsigned RGB samples into row-major channel planes`() {
        val samples = intArrayOf(0, 64, 128, 255)
        val normalized = floatArrayOf(-1f, NORMALIZED_64, NORMALIZED_128, 1f)
        val source =
            writeImage(BufferedImage.TYPE_INT_RGB) { x, y ->
                (samples[x % 4] shl 16) or (samples[y % 4] shl 8) or samples[(x + 2 * y) % 4]
            }
        val tensor = process(source)
        assertBufferLayout(tensor)

        val width = Siglip2TensorTransformation.width.value
        val height = Siglip2TensorTransformation.height.value
        val planeSize = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * width + x
                withClue("pixel ($x, $y)") {
                    tensor.values.get(offset) shouldBe (normalized[x % 4] plusOrMinus 0.000001f)
                    tensor.values.get(planeSize + offset) shouldBe (normalized[y % 4] plusOrMinus 0.000001f)
                    tensor.values.get(2 * planeSize + offset) shouldBe (normalized[(x + 2 * y) % 4] plusOrMinus 0.000001f)
                }
            }
        }
        tensor.values.position() shouldBe 0
    }

    @Test
    fun `flattens transparent pixels on white before tensor normalization`() {
        val source =
            writeImage(BufferedImage.TYPE_INT_ARGB) { x, _ ->
                if (x % 2 == 0) 0x00804020 else 0xff804020.toInt()
            }
        val tensor = process(source)
        assertBufferLayout(tensor)

        val planeSize = Siglip2TensorTransformation.width.value * Siglip2TensorTransformation.height.value
        val opaqueChannels = floatArrayOf(NORMALIZED_128, NORMALIZED_64, NORMALIZED_32)
        for (channel in 0..2) {
            for (offset in 0 until planeSize) {
                val expected = if (offset % 2 == 0) 1f else opaqueChannels[channel]
                withClue("channel $channel, pixel $offset") {
                    tensor.values.get(channel * planeSize + offset) shouldBe (expected plusOrMinus 0.000001f)
                }
            }
        }
        tensor.values.position() shouldBe 0
    }

    private fun assertBufferLayout(tensor: ImageTensor) {
        tensor.shape.toList() shouldBe listOf(1L, 3L, 224L, 224L)
        tensor.values.isDirect shouldBe true
        tensor.values.order() shouldBe ByteOrder.nativeOrder()
        tensor.values.position() shouldBe 0
        tensor.values.limit() shouldBe 3 * 224 * 224
        tensor.values.capacity() shouldBe 3 * 224 * 224
        tensor.values.remaining() shouldBe 3 * 224 * 224
    }

    private fun process(source: Path): ImageTensor =
        Arena.ofConfined().use { arena ->
            processor.process(
                arena = arena,
                sourceFile = source,
                sourceFormat = ImageFormat.PNG,
                tensorTransformation = Siglip2TensorTransformation,
            )
        }

    private fun writeImage(
        type: Int,
        pixel: (Int, Int) -> Int,
    ): Path {
        val image = BufferedImage(224, 224, type)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, pixel(x, y))
            }
        }
        return temporaryDirectory.resolve("source.png").also {
            ImageIO.write(image, "png", it.toFile()) shouldBe true
        }
    }
}
