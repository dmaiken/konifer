package io.konifer.infrastructure.variant

import io.konifer.BaseUnitTest
import io.konifer.domain.image.Fit
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.ports.TransformationDataContainerV2
import io.konifer.domain.variant.Transformation
import io.konifer.getResourceAsFile
import io.konifer.infrastructure.vips.VipsImageProcessor
import io.konifer.service.TemporaryFileFactory
import io.konifer.service.TemporaryFileFactory.createProcessedVariantTempFile
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

class CoroutineVariantGeneratorTest : BaseUnitTest() {
    private val imageProcessor =
        spyk<VipsImageProcessor>(
            VipsImageProcessor(),
        )
    private val channel = Channel<ImageProcessingJob<*>>()

    // This is needed despite what Intellij thinks - it consumes from the scheduler
    private val coroutineVariantGenerator =
        CoroutineVariantGenerator(
            imageProcessor = imageProcessor,
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
                val result = CompletableDeferred<Boolean>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainerV2(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
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
                val result = CompletableDeferred<Boolean>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainerV2(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
                                        ),
                                    output = output1,
                                ),
                                TransformationDataContainerV2(
                                    transformation =
                                        Transformation(
                                            height = 100,
                                            width = 100,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
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
                val result = CompletableDeferred<Boolean>()
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
                val result = CompletableDeferred<Boolean>()
                val variantGenerationJob =
                    GenerateVariantsJob(
                        source = source,
                        transformationDataContainers =
                            listOf(
                                TransformationDataContainerV2(
                                    transformation =
                                        Transformation(
                                            height = 200,
                                            width = 200,
                                            format = ImageFormat.PNG,
                                            fit = Fit.FILL,
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
                result.await() shouldBe false

                val newResult = CompletableDeferred<Boolean>()
                channel.send(variantGenerationJob.copy(deferredResult = newResult))
                newResult.await() shouldBe true
            }
    }
}
