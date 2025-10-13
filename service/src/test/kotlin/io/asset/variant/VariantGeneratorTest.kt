package io.asset.variant

import io.asset.handler.RequestedTransformationNormalizer
import io.asset.handler.StoreAssetDto
import io.asset.model.AssetAndVariants
import io.asset.model.StoreAssetRequest
import io.asset.repository.InMemoryAssetRepository
import io.asset.store.InMemoryObjectStore
import io.aws.S3Properties
import io.createRequestedImageTransformation
import io.image.DimensionCalculator.calculateDimensions
import io.image.lqip.ImagePreviewGenerator
import io.image.model.Attributes
import io.image.model.Fit
import io.image.model.ImageFormat
import io.image.model.ImageProperties
import io.image.model.LQIPs
import io.image.model.Transformation
import io.image.vips.VipsEncoder
import io.image.vips.VipsImageProcessor
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import io.mockk.coEvery
import io.mockk.spyk
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class VariantGeneratorTest {
    companion object {
        private const val PATH = "root.profile.123"
        private const val BUCKET = "assets"
    }

    private val variantParameterGenerator = VariantParameterGenerator()
    private val assetRepository = InMemoryAssetRepository(variantParameterGenerator)
    private val objectStore = InMemoryObjectStore()
    private val requestedTransformationNormalizer =
        RequestedTransformationNormalizer(
            InMemoryAssetRepository(variantParameterGenerator),
        )
    private val imageProcessor =
        spyk<VipsImageProcessor>(
            VipsImageProcessor(ImagePreviewGenerator(), requestedTransformationNormalizer, VipsEncoder()),
        )
    private val channel = Channel<VariantGenerationJob>()

    private val variantGenerator =
        VariantGenerator(
            assetRepository = assetRepository,
            objectStore = objectStore,
            imageProcessor = imageProcessor,
            channel = channel,
            requestedTransformationNormalizer = requestedTransformationNormalizer,
        )

    private lateinit var asset: AssetAndVariants
    private lateinit var bufferedImage: BufferedImage

    @BeforeEach
    fun beforeEach(): Unit =
        runBlocking {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readAllBytes()
            bufferedImage = ImageIO.read(ByteArrayInputStream(image))
            val channel = ByteChannel(autoFlush = true)
            val resultDeferred =
                async {
                    objectStore.persist(BUCKET, channel, image.size.toLong())
                }
            channel.writeFully(image)
            channel.close()
            val objectStoreResponse = resultDeferred.await()

            asset =
                assetRepository.store(
                    StoreAssetDto(
                        mimeType = "image/png",
                        path = PATH,
                        request =
                            StoreAssetRequest(
                                type = "image/png",
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
                    ),
                )
        }

    @Test
    fun `can generate variant from channel`() =
        runTest {
            val result = CompletableDeferred<AssetAndVariants>()
            val variantGenerationJob =
                VariantGenerationJob(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformations =
                        listOf(
                            createRequestedImageTransformation(
                                height = 50,
                                fit = Fit.FIT,
                            ),
                        ),
                    deferredResult = result,
                    pathConfiguration = PathConfiguration.DEFAULT,
                )
            channel.send(variantGenerationJob)

            val (expectedWidth, expectedHeight) =
                calculateDimensions(
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
                VariantGenerationJob(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformations =
                        listOf(
                            createRequestedImageTransformation(
                                height = 50,
                                fit = Fit.FIT,
                            ),
                        ),
                    deferredResult = result,
                    pathConfiguration =
                        PathConfiguration.create(
                            allowedContentTypes = null,
                            imageProperties = ImageProperties.DEFAULT,
                            eagerVariants = emptyList(),
                            s3Properties = S3Properties.create("different-bucket"),
                        ),
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
                VariantGenerationJob(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformations =
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
                    pathConfiguration = PathConfiguration.DEFAULT,
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
    fun `can generate variant synchronously`() =
        runTest {
            val result =
                variantGenerator.generateVariant(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformation =
                        Transformation(
                            height = 50,
                            width = 50,
                            format = ImageFormat.PNG,
                            fit = Fit.FIT,
                        ),
                    pathConfiguration = PathConfiguration.DEFAULT,
                )

            result.variants shouldHaveSize 1
            result.variants.forExactly(1) {
                it.isOriginalVariant shouldBe false
                it.transformation.height shouldNotBe bufferedImage.width
                it.objectStoreBucket shouldBe BUCKET
            }
        }

    @Test
    fun `variant created synchronously uses supplied bucket`() =
        runTest {
            val result =
                variantGenerator.generateVariant(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformation =
                        Transformation(
                            height = 50,
                            width = 50,
                            format = ImageFormat.PNG,
                            fit = Fit.FIT,
                        ),
                    pathConfiguration =
                        PathConfiguration.create(
                            allowedContentTypes = null,
                            imageProperties = ImageProperties.DEFAULT,
                            eagerVariants = emptyList(),
                            s3Properties = S3Properties.create("different-bucket"),
                        ),
                )

            result.variants shouldHaveSize 1
            result.variants.forExactly(1) {
                it.isOriginalVariant shouldBe false
                it.transformation.height shouldNotBe bufferedImage.width
                it.objectStoreBucket shouldBe "different-bucket"
            }
        }

    @Test
    fun `if original asset does not exist then exception is thrown`() =
        runTest {
            shouldThrow<IllegalStateException> {
                variantGenerator.generateVariant(
                    treePath = "does.not.exist",
                    entryId = asset.asset.entryId,
                    transformation =
                        Transformation(
                            height = 50,
                            width = 50,
                            format = ImageFormat.PNG,
                            fit = Fit.FIT,
                        ),
                    pathConfiguration = PathConfiguration.DEFAULT,
                )
            }
        }

    @Test
    fun `if no variants are in request then nothing is processed`() =
        runTest {
            val result = CompletableDeferred<AssetAndVariants>()
            val variantGenerationJob =
                VariantGenerationJob(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformations = listOf(),
                    deferredResult = result,
                    pathConfiguration = PathConfiguration.DEFAULT,
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
                VariantGenerationJob(
                    treePath = asset.asset.path,
                    entryId = asset.asset.entryId,
                    transformations =
                        listOf(
                            createRequestedImageTransformation(
                                height = 50,
                            ),
                        ),
                    deferredResult = result,
                    pathConfiguration = PathConfiguration.DEFAULT,
                )

            coEvery {
                imageProcessor.generateVariant(any(), PathConfiguration.DEFAULT, any(), any(), any())
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
