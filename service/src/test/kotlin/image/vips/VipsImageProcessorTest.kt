package io.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import asset.repository.InMemoryAssetRepository
import asset.variant.VariantParameterGenerator
import com.vanniktech.blurhash.BlurHash
import image.VipsImageProcessor
import image.lqip.ThumbHash
import image.model.ImageFormat
import image.model.ImageProperties
import image.model.PreProcessingProperties
import io.asset.AssetStreamContainer
import io.asset.handler.RequestedTransformationNormalizer
import io.aws.S3Properties
import io.createPreProcessingProperties
import io.image.lqip.ImagePreviewGenerator
import io.image.lqip.LQIPImplementation
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.writeFully
import io.matcher.shouldBeApproximately
import io.matcher.shouldBeWithinOneOf
import io.matcher.shouldHaveSamePixelContentAs
import io.mockk.spyk
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.apache.tika.Tika
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

class VipsImageProcessorTest {
    private val imagePreviewGenerator = spyk<ImagePreviewGenerator>()
    private val requestedTransformationNormalizer = RequestedTransformationNormalizer(InMemoryAssetRepository(VariantParameterGenerator()))

    private val vipsImageProcessor = VipsImageProcessor(imagePreviewGenerator, requestedTransformationNormalizer, VipsEncoder())

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Vips.init()
        }
    }

    @Nested
    inner class PreProcessTests {
        @Test
        fun `image is not preprocessed if not enabled`() =
            runTest {
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteChannel(true)
                launch {
                    imageChannel.writeFully(image)
                    imageChannel.close()
                }
                val bufferedImage = ImageIO.read(ByteArrayInputStream(image))
                val pathConfig =
                    PathConfiguration.create(
                        allowedContentTypes = null,
                        imageProperties =
                            ImageProperties.create(
                                preProcessing = PreProcessingProperties.DEFAULT,
                                lqip = setOf(),
                            ),
                        eagerVariants = emptyList(),
                        s3Properties = S3Properties.DEFAULT,
                    )

                val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.PNG, pathConfig, outputChannel)
                val outputBytes = outputBytesDeferred.await()
                processedImage.attributes.format shouldBe ImageFormat.PNG
                processedImage.attributes.height shouldBe bufferedImage.height
                processedImage.attributes.width shouldBe bufferedImage.width

                ImageIO.read(ByteArrayInputStream(outputBytes)) shouldHaveSamePixelContentAs bufferedImage
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `png image is converted to supported image format`(format: ImageFormat) =
            runTest {
                testImageFormatConversion(ImageFormat.PNG, format)
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `jpeg image is converted to supported image format`(format: ImageFormat) =
            runTest {
                testImageFormatConversion(ImageFormat.JPEG, format)
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `webp image is converted to supported image format`(format: ImageFormat) =
            runTest {
                testImageFormatConversion(ImageFormat.WEBP, format)
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `avif image is converted to supported image format`(format: ImageFormat) =
            runTest {
                testImageFormatConversion(ImageFormat.AVIF, format)
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `image is resized by maxHeight if enabled when preprocessing`(format: ImageFormat) =
            runTest {
                val maxHeight = 200
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteChannel(true)
                launch {
                    imageChannel.writeFully(image)
                    imageChannel.close()
                }
                val pathConfig =
                    PathConfiguration.create(
                        allowedContentTypes = null,
                        imageProperties =
                            ImageProperties.create(
                                preProcessing =
                                    createPreProcessingProperties(
                                        maxHeight = maxHeight,
                                    ),
                                lqip = setOf(),
                            ),
                        eagerVariants = emptyList(),
                        s3Properties = S3Properties.DEFAULT,
                    )

                val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage = vipsImageProcessor.preprocess(container, format, pathConfig, outputChannel)
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe format
                Tika().detect(outputBytes) shouldBe format.mimeType
                Vips.run { arena ->
                    val sourceVImage = VImage.newFromBytes(arena, image)
                    val processedVImage = VImage.newFromBytes(arena, outputBytes)

                    processedVImage.height shouldBe maxHeight
                    processedVImage.aspectRatio() shouldBeApproximately sourceVImage.aspectRatio()

                    processedImage.attributes.height shouldBe processedVImage.height
                    processedImage.attributes.width shouldBe processedVImage.width
                }
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `image is resized by maxWidth if enabled when preprocessing`(format: ImageFormat) =
            runTest {
                val maxWidth = 200
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${format.extension}")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteChannel(true)
                launch {
                    imageChannel.writeFully(image)
                    imageChannel.close()
                }
                val pathConfig =
                    PathConfiguration.create(
                        allowedContentTypes = null,
                        imageProperties =
                            ImageProperties.create(
                                preProcessing =
                                    createPreProcessingProperties(
                                        maxWidth = maxWidth,
                                    ),
                                lqip = setOf(),
                            ),
                        eagerVariants = emptyList(),
                        s3Properties = S3Properties.DEFAULT,
                    )

                val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage = vipsImageProcessor.preprocess(container, format, pathConfig, outputChannel)
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe format
                Tika().detect(outputBytes) shouldBe format.mimeType
                Vips.run { arena ->
                    val sourceVImage = VImage.newFromBytes(arena, image)
                    val processedVImage = VImage.newFromBytes(arena, outputBytes)

                    processedVImage.width shouldBeWithinOneOf maxWidth
                    processedVImage.aspectRatio() shouldBeApproximately sourceVImage.aspectRatio()

                    processedImage.attributes.height shouldBeWithinOneOf processedVImage.height
                    processedImage.attributes.width shouldBeWithinOneOf processedVImage.width
                }
            }

        @Test
        fun `image lqips are not generated if not enabled when preprocessing`() =
            runTest {
                val maxWidth = 200
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.jpeg")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteChannel(true)
                launch {
                    imageChannel.writeFully(image)
                    imageChannel.close()
                }
                val pathConfig =
                    PathConfiguration.create(
                        allowedContentTypes = null,
                        imageProperties =
                            ImageProperties.create(
                                preProcessing =
                                    createPreProcessingProperties(
                                        maxWidth = maxWidth,
                                    ),
                                lqip = setOf(),
                            ),
                        eagerVariants = emptyList(),
                        s3Properties = S3Properties.DEFAULT,
                    )

                val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG, pathConfig, outputChannel)
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe ImageFormat.JPEG
                Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
                processedImage.lqip.blurhash shouldBe null
                processedImage.lqip.thumbhash shouldBe null
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `image blurhash is generated regardless of whether preprocessing is enabled when preprocessing`(preprocessingEnabled: Boolean) =
            runTest {
                val image =
                    javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.jpeg")!!.use {
                        it.readBytes()
                    }
                val imageChannel = ByteChannel(true)
                launch {
                    imageChannel.writeFully(image)
                    imageChannel.close()
                }
                val pathConfig =
                    PathConfiguration.create(
                        allowedContentTypes = null,
                        imageProperties =
                            ImageProperties.create(
                                preProcessing =
                                    createPreProcessingProperties(
                                        maxWidth = if (preprocessingEnabled) 100 else null,
                                        maxHeight = if (preprocessingEnabled) 100 else null,
                                    ),
                                lqip = setOf(LQIPImplementation.BLURHASH),
                            ),
                        eagerVariants = emptyList(),
                        s3Properties = S3Properties.DEFAULT,
                    )

                val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG, pathConfig, outputChannel)
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe ImageFormat.JPEG
                Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
                processedImage.lqip.blurhash shouldNotBe null
                processedImage.lqip.thumbhash shouldBe null
                Vips.run { arena ->
                    val processedVImage = VImage.newFromBytes(arena, outputBytes)
                    shouldNotThrowAny {
                        BlurHash.decode(processedImage.lqip.blurhash!!, processedVImage.width, processedVImage.height)
                    }
                }
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `image thumbhash is generated regardless of whether preprocessing is enabled when preprocessing`(
            preprocessingEnabled: Boolean,
        ) = runTest {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.jpeg")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteChannel(true)
            launch {
                imageChannel.writeFully(image)
                imageChannel.close()
            }
            val pathConfig =
                PathConfiguration.create(
                    allowedContentTypes = null,
                    imageProperties =
                        ImageProperties.create(
                            preProcessing =
                                createPreProcessingProperties(
                                    maxWidth = if (preprocessingEnabled) 100 else null,
                                    maxHeight = if (preprocessingEnabled) 100 else null,
                                ),
                            lqip = setOf(LQIPImplementation.THUMBHASH),
                        ),
                    eagerVariants = emptyList(),
                    s3Properties = S3Properties.DEFAULT,
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.format shouldBe ImageFormat.JPEG
            Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
            processedImage.lqip.blurhash shouldBe null
            processedImage.lqip.thumbhash shouldNotBe null

            shouldNotThrowAny {
                ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(processedImage.lqip.thumbhash))
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `image lqips are generated regardless of image format when preprocessing`(format: ImageFormat) =
            runTest {
                testImageFormatConversion(ImageFormat.JPEG, format, LQIPImplementation.entries.toSet())
            }

        private suspend fun testImageFormatConversion(
            from: ImageFormat,
            to: ImageFormat,
            lqips: Set<LQIPImplementation> = setOf(),
        ) = coroutineScope {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.${from.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteChannel(true)
            launch {
                imageChannel.writeFully(image)
                imageChannel.close()
            }
            val pathConfig =
                PathConfiguration.create(
                    allowedContentTypes = null,
                    imageProperties =
                        ImageProperties.create(
                            preProcessing =
                                createPreProcessingProperties(
                                    format = to,
                                ),
                            lqip = lqips,
                        ),
                    eagerVariants = emptyList(),
                    s3Properties = S3Properties.DEFAULT,
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, from, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.format shouldBe to
            Tika().detect(outputBytes) shouldBe to.mimeType
            if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                shouldNotThrowAny {
                    ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(processedImage.lqip.thumbhash))
                }
            }
            Vips.run { arena ->
                val sourceVImage = VImage.newFromBytes(arena, image)
                val processedVImage = VImage.newFromBytes(arena, outputBytes)

                sourceVImage.height shouldBe processedVImage.height
                sourceVImage.width shouldBe processedVImage.width

                processedImage.attributes.height shouldBe processedVImage.height
                processedImage.attributes.width shouldBe processedVImage.width

                if (lqips.contains(LQIPImplementation.BLURHASH)) {
                    shouldNotThrowAny {
                        BlurHash.decode(processedImage.lqip.blurhash!!, processedVImage.width, processedVImage.height)
                    }
                }
            }
        }
    }
}
