package io.konifer.infrastructure.variant

import io.konifer.BaseUnitTest
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.variant.Transformation
import io.konifer.getResourceAsFile
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.TemporaryFileFactory.createProcessedVariantTempFile
import io.konifer.infrastructure.vips.processor.VipsImageProcessor
import io.konifer.infrastructure.vips.processor.VipsTensorProcessor
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.file.PathUtils.deleteOnExit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class CoroutineImageGeneratorTest : BaseUnitTest() {
    private val imageProcessor =
        spyk<VipsImageProcessor>(
            VipsImageProcessor(),
        )
    private val tensorProcessor =
        spyk<VipsTensorProcessor>(
            VipsTensorProcessor(),
        )
    private val channel = Channel<ImageProcessingJob<*>>()

    // This is needed despite what Intellij thinks - it consumes from the scheduler
    private val coroutineImageGenerator =
        CoroutineImageGenerator(
            imageProcessor = imageProcessor,
            tensorProcessor = tensorProcessor,
            consumer =
                PriorityChannelConsumer(
                    highPriorityChannel = channel,
                    backgroundChannel = Channel(),
                    highPriorityWeight = 80,
                ),
            numberOfWorkers = 4,
        )

    lateinit var source: Path
    private lateinit var bufferedImage: BufferedImage

    @BeforeEach
    fun beforeEach(): Unit =
        runBlocking {
            val imageFile = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            bufferedImage = ImageIO.read(ByteArrayInputStream(imageFile.readBytes()))
            source =
                TemporaryFileFactory.createOriginalVariantTempFile(ImageFormat.PNG.extension).apply {
                    deleteOnExit(this)
                    writeBytes(imageFile.readBytes())
                }
        }

    @Nested
    inner class VariantGenerationTests {
        @Test
        fun `can generate variant from channel`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationJob)
                result.await()

                val outputImage = ImageIO.read(ByteArrayInputStream(output.toByteArray()))
                outputImage.width shouldBe 200
                outputImage.height shouldBe 200
            }

        @Test
        fun `can generate multiple variants for same image through single channel request`() =
            runTest {
                val output1 = ByteChannel()
                val output2 = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output1,
                                ),
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 100,
                                            width = 100,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output2,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationJob)
                result.await()

                val outputImage1 = ImageIO.read(ByteArrayInputStream(output1.toByteArray()))
                outputImage1.width shouldBe 200
                outputImage1.height shouldBe 200
                val outputImage2 = ImageIO.read(ByteArrayInputStream(output2.toByteArray()))
                outputImage2.width shouldBe 100
                outputImage2.height shouldBe 100
            }

        @Test
        fun `if no variants are in request then nothing is processed`() =
            runTest {
                val output =
                    createProcessedVariantTempFile(ImageFormat.PNG.extension).apply {
                        deleteOnExit(this)
                    }
                val result = CompletableDeferred<Unit>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers = listOf(),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )
                channel.send(variantGenerationJob)
                result.await()
                output.exists() shouldBe false
            }

        @Test
        fun `if variant fails to generate then channel is still live`() =
            runTest {
                val output = ByteChannel()
                val result = CompletableDeferred<Unit>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                    output = output,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                    )

                coEvery {
                    imageProcessor.generateVariants(
                        source = any(),
                        transformationDataContainers = any(),
                        lqipImplementations = any(),
                    )
                }.throws(RuntimeException())
                    .coAndThen { callOriginal() }

                channel.send(variantGenerationJob)
                shouldThrow<RuntimeException> { result.await() }

                val newResult = CompletableDeferred<Unit>()
                channel.send(variantGenerationJob.copy(deferredResult = newResult))
                shouldNotThrowAny { newResult.await() }
            }
    }
}
