package io.image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import com.vanniktech.blurhash.BlurHash
import image.VipsImageProcessor
import image.lqip.ThumbHash
import image.model.ImageFormat
import image.model.ImageProperties
import image.model.PreProcessingProperties
import io.asset.AssetStreamContainer
import io.image.hash.ImagePreviewGenerator
import io.image.hash.LQIPImplementation
import io.image.vips.aspectRatio
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.writeFully
import io.matcher.shouldBeApproximately
import io.mockk.spyk
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.apache.tika.Tika
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

class VipsImageProcessorTest {
    private val imagePreviewGenerator = spyk<ImagePreviewGenerator>()

    private val vipsImageProcessor = VipsImageProcessor(imagePreviewGenerator)

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Vips.init()
        }
    }

    @Test
    @Disabled
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
                            preProcessing = PreProcessingProperties.default(),
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.PNG.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()
            processedImage.attributes.mimeType shouldBe ImageFormat.PNG.mimeType
            processedImage.attributes.height shouldBe bufferedImage.height
            processedImage.attributes.width shouldBe bufferedImage.width

            container.readAll().forEachIndexed { index, imageByte ->
                outputBytes[index] shouldBe imageByte
            }
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
    fun `image is resized by height if enabled`(format: ImageFormat) =
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
                                PreProcessingProperties.create(
                                    maxWidth = null,
                                    maxHeight = maxHeight,
                                    imageFormat = null,
                                ),
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, format.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.mimeType shouldBe format.mimeType
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
    fun `image is resized by width if enabled`(format: ImageFormat) =
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
                                PreProcessingProperties.create(
                                    maxWidth = maxWidth,
                                    maxHeight = null,
                                    imageFormat = null,
                                ),
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, format.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.mimeType shouldBe format.mimeType
            Tika().detect(outputBytes) shouldBe format.mimeType
            Vips.run { arena ->
                val sourceVImage = VImage.newFromBytes(arena, image)
                val processedVImage = VImage.newFromBytes(arena, outputBytes)

                processedVImage.width shouldBe maxWidth
                processedVImage.aspectRatio() shouldBeApproximately sourceVImage.aspectRatio()

                processedImage.attributes.height shouldBe processedVImage.height
                processedImage.attributes.width shouldBe processedVImage.width
            }
        }

    @Test
    fun `image lqips are not generated if not enabled`() =
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
                                PreProcessingProperties.create(
                                    maxWidth = maxWidth,
                                    maxHeight = null,
                                    imageFormat = null,
                                ),
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.mimeType shouldBe ImageFormat.JPEG.mimeType
            Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
            processedImage.lqip.blurhash shouldBe null
            processedImage.lqip.thumbhash shouldBe null
        }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `image blurhash is generated regardless of whether preprocessing is enabled`(preprocessingEnabled: Boolean) =
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
                                PreProcessingProperties.create(
                                    maxWidth = if (preprocessingEnabled) 100 else null,
                                    maxHeight = if (preprocessingEnabled) 100 else null,
                                    imageFormat = null,
                                ),
                            lqip = setOf(LQIPImplementation.BLURHASH),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.mimeType shouldBe ImageFormat.JPEG.mimeType
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
    fun `image thumbhash is generated regardless of whether preprocessing is enabled`(preprocessingEnabled: Boolean) =
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
                                PreProcessingProperties.create(
                                    maxWidth = if (preprocessingEnabled) 100 else null,
                                    maxHeight = if (preprocessingEnabled) 100 else null,
                                    imageFormat = null,
                                ),
                            lqip = setOf(LQIPImplementation.THUMBHASH),
                        ),
                    eagerVariants = emptyList(),
                )

            val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
            val outputChannel = ByteChannel(true)
            val outputBytesDeferred =
                async {
                    outputChannel.toInputStream().readAllBytes()
                }
            val processedImage = vipsImageProcessor.preprocess(container, ImageFormat.JPEG.mimeType, pathConfig, outputChannel)
            val outputBytes = outputBytesDeferred.await()

            processedImage.attributes.mimeType shouldBe ImageFormat.JPEG.mimeType
            Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
            processedImage.lqip.blurhash shouldBe null
            processedImage.lqip.thumbhash shouldNotBe null

            shouldNotThrowAny {
                ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(processedImage.lqip.thumbhash))
            }
        }

    @ParameterizedTest
    @EnumSource(ImageFormat::class)
    fun `image lqips are generated regardless of image format`(format: ImageFormat) =
        runTest {
            testImageFormatConversion(ImageFormat.JPEG, format, LQIPImplementation.entries.toSet())
        }

    @Test
    fun `image lqips are not modified when generating variant without color changes`() =
        runTest {
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
                            PreProcessingProperties.create(
                                maxWidth = null,
                                maxHeight = null,
                                imageFormat = to,
                            ),
                        lqip = lqips,
                    ),
                eagerVariants = emptyList(),
            )

        val container = AssetStreamContainer.fromReadChannel(this, imageChannel)
        val outputChannel = ByteChannel(true)
        val outputBytesDeferred =
            async {
                outputChannel.toInputStream().readAllBytes()
            }
        val processedImage = vipsImageProcessor.preprocess(container, from.mimeType, pathConfig, outputChannel)
        val outputBytes = outputBytesDeferred.await()

        processedImage.attributes.mimeType shouldBe to.mimeType
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
