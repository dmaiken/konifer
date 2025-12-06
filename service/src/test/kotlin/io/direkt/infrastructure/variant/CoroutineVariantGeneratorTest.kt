package io.direkt.infrastructure.variant

import io.createRequestedImageTransformation
import io.direkt.domain.asset.AssetSource
import io.direkt.service.transformation.TransformationNormalizer
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.model.AssetAndVariants
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.infrastructure.database.inmemory.InMemoryAssetRepository
import io.direkt.infrastructure.objectstore.inmemory.InMemoryObjectRepository
import io.direkt.getResourceAsFile
import io.direkt.infrastructure.vips.DimensionCalculator
import io.direkt.domain.image.Attributes
import io.direkt.domain.image.Fit
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPs
import io.direkt.domain.image.Transformation
import io.direkt.infrastructure.vips.VipsImageProcessor
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class CoroutineVariantGeneratorTest {
    companion object {
        private const val PATH = "root.profile.123"
        private const val BUCKET = "assets"
    }

    private val assetRepository = InMemoryAssetRepository()
    private val objectStore = InMemoryObjectRepository()
    private val imageProcessor =
        spyk<VipsImageProcessor>(
            VipsImageProcessor(),
        )
    private val channel = Channel<ImageProcessingJob<*>>()
    private val consumer =
        PriorityChannelConsumer(
            highPriorityChannel = channel,
            backgroundChannel = Channel(),
            highPriorityWeight = 80,
        )

    // This is needed despite what Intellij thinks - it consumes from the scheduler
    private val coroutineVariantGenerator =
        CoroutineVariantGenerator(
            assetRepository = assetRepository,
            objectStore = objectStore,
            imageProcessor = imageProcessor,
            consumer = consumer,
            transformationNormalizer =
                TransformationNormalizer(
                    InMemoryAssetRepository(),
                ),
            numberOfWorkers = 8,
        )

    private lateinit var asset: AssetAndVariants
    private lateinit var bufferedImage: BufferedImage

    @BeforeEach
    fun beforeEach(): Unit =
        runBlocking {
            val image = javaClass.getResourceAsFile("/images/joshua-tree/joshua-tree.png")
            bufferedImage = ImageIO.read(ByteArrayInputStream(image.readBytes()))
            val objectStoreResponse = objectStore.persist(BUCKET, image, ImageFormat.PNG)

            asset =
                assetRepository.store(
                    StoreAssetDto(
                        path = PATH,
                        request =
                            StoreAssetRequest(
                                alt = "an image",
                            ),
                        attributes =
                            Attributes(
                                width = bufferedImage.width,
                                height = bufferedImage.height,
                                format = ImageFormat.PNG,
                            ),
                        persistResult = objectStoreResponse,
                        lqips = LQIPs.NONE,
                        source = AssetSource.UPLOAD,
                    ),
                )
        }

    @Nested
    inner class EagerVariantGenerationTests {
        @Test
        fun `can generate variant from channel`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val variantGenerationJob =
                    EagerVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requestedTransformations =
                            listOf(
                                createRequestedImageTransformation(
                                    height = 50,
                                    fit = Fit.FIT,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )
                channel.send(variantGenerationJob)

                val (expectedWidth, expectedHeight) =
                    DimensionCalculator.calculateDimensions(
                        bufferedImage = bufferedImage,
                        height = 50,
                        width = null,
                        fit = Fit.FIT,
                    )
                expectedHeight shouldBe 50
                result.await().apply {
                    variants shouldHaveSize 1
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation.apply {
                            height shouldBe 50
                            width shouldBe expectedWidth
                            fit shouldBe Fit.FIT
                            format shouldBe ImageFormat.PNG
                        }

                        it.objectStoreBucket shouldBe BUCKET
                    }
                }
            }

        @Test
        fun `variant created from channel uses supplied bucket`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val variantGenerationJob =
                    EagerVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requestedTransformations =
                            listOf(
                                createRequestedImageTransformation(
                                    height = 50,
                                    fit = Fit.FIT,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = "different-bucket",
                    )
                channel.send(variantGenerationJob)

                result.await().apply {
                    variants shouldHaveSize 1
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation.height shouldNotBe bufferedImage.width
                        it.objectStoreBucket shouldBe "different-bucket"
                    }
                }
            }

        @Test
        fun `can generate multiple variants for same image through single channel request`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val variantGenerationJob =
                    EagerVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requestedTransformations =
                            listOf(
                                createRequestedImageTransformation(
                                    height = 50,
                                    fit = Fit.FIT,
                                ),
                                createRequestedImageTransformation(
                                    width = 50,
                                    format = ImageFormat.AVIF,
                                    fit = Fit.FIT,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )
                channel.send(variantGenerationJob)

                result.await().apply {
                    variants shouldHaveSize 2
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation.height shouldBe 50
                        it.transformation.width shouldNotBe bufferedImage.width
                        it.objectStoreBucket shouldBe BUCKET
                    }
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation.height shouldNotBe bufferedImage.height
                        it.transformation.width shouldBe 50
                        it.transformation.format shouldBe ImageFormat.AVIF
                        it.objectStoreBucket shouldBe BUCKET
                    }
                }
            }

        @Test
        fun `if original asset does not exist then exception is thrown`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                channel.send(
                    EagerVariantGenerationJob(
                        path = "does.not.exist",
                        entryId = asset.asset.entryId,
                        requestedTransformations =
                            listOf(
                                createRequestedImageTransformation(
                                    height = 400,
                                ),
                            ),
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                        deferredResult = result,
                    ),
                )

                shouldThrow<IllegalStateException> {
                    result.await()
                }
            }

        @Test
        fun `if no variants are in request then nothing is processed`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val variantGenerationJob =
                    EagerVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requestedTransformations = emptyList(),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )
                channel.send(variantGenerationJob)

                shouldThrow<IllegalArgumentException> {
                    result.await()
                }
            }

        @Test
        fun `if variant fails to generate then channel is still live`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val variantGenerationJob =
                    EagerVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        requestedTransformations =
                            listOf(
                                createRequestedImageTransformation(
                                    height = 50,
                                ),
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )

                coEvery {
                    imageProcessor.generateVariant(any(), emptySet(), any(), any())
                }.throws(RuntimeException())
                    .coAndThen { callOriginal() }

                channel.send(variantGenerationJob)

                shouldThrow<RuntimeException> {
                    result.await()
                }

                val newResult = CompletableDeferred<AssetAndVariants>()
                channel.send(variantGenerationJob.copy(deferredResult = newResult))

                shouldNotThrowAny {
                    newResult.await()
                }
            }
    }

    @Nested
    inner class VariantGenerationTests {
        @Test
        fun `can generate variant from channel`() =
            runTest {
                val transformation =
                    Transformation(
                        width = 150,
                        height = 100,
                        fit = Fit.FILL,
                        format = ImageFormat.PNG,
                    )
                val result = CompletableDeferred<AssetAndVariants>()
                val onDemandVariantGenerationJob =
                    OnDemandVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        transformation = transformation,
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )
                channel.send(onDemandVariantGenerationJob)

                result.await().apply {
                    variants shouldHaveSize 1
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation shouldBe transformation
                        it.objectStoreBucket shouldBe BUCKET
                    }
                }
            }

        @Test
        fun `variant created from channel uses supplied bucket`() =
            runTest {
                val transformation =
                    Transformation(
                        width = 150,
                        height = 100,
                        fit = Fit.FILL,
                        format = ImageFormat.PNG,
                    )
                val result = CompletableDeferred<AssetAndVariants>()
                val onDemandVariantGenerationJob =
                    OnDemandVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        transformation = transformation,
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = "different-bucket",
                    )
                channel.send(onDemandVariantGenerationJob)

                result.await().apply {
                    variants shouldHaveSize 1
                    variants.forExactly(1) {
                        it.isOriginalVariant shouldBe false
                        it.transformation shouldBe transformation
                        it.objectStoreBucket shouldBe "different-bucket"
                    }
                }
            }

        @Test
        fun `if original asset does not exist then exception is thrown`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                channel.send(
                    OnDemandVariantGenerationJob(
                        path = "does.not.exist",
                        entryId = asset.asset.entryId,
                        transformation =
                            Transformation(
                                height = 50,
                                width = 50,
                                format = ImageFormat.PNG,
                                fit = Fit.FIT,
                            ),
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                        deferredResult = result,
                    ),
                )

                shouldThrow<IllegalStateException> {
                    result.await()
                }
            }

        @Test
        fun `if variant fails to generate then channel is still live`() =
            runTest {
                val result = CompletableDeferred<AssetAndVariants>()
                val onDemandVariantGenerationJob =
                    OnDemandVariantGenerationJob(
                        path = asset.asset.path,
                        entryId = asset.asset.entryId,
                        transformation =
                            Transformation(
                                height = 100,
                                width = 100,
                                format = ImageFormat.PNG,
                            ),
                        deferredResult = result,
                        lqipImplementations = emptySet(),
                        bucket = BUCKET,
                    )

                coEvery {
                    imageProcessor.generateVariant(any(), emptySet(), any(), any())
                }.throws(RuntimeException())
                    .coAndThen { callOriginal() }

                channel.send(onDemandVariantGenerationJob)

                shouldThrow<RuntimeException> {
                    result.await()
                }

                val newResult = CompletableDeferred<AssetAndVariants>()
                channel.send(onDemandVariantGenerationJob.copy(deferredResult = newResult))

                shouldNotThrowAny {
                    newResult.await()
                }
            }
    }
}
