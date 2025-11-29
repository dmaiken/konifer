package io.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import com.vanniktech.blurhash.BlurHash
import io.asset.AssetStreamContainer
import io.image.lqip.LQIPImplementation
import io.image.model.Fit
import io.image.model.ImageFormat
import io.image.model.Transformation
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.writeFully
import io.lqip.image.ThumbHash
import io.matchers.shouldHaveSamePixelContentAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.apache.tika.Tika
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

class VipsImageProcessorTest {
    private val vipsImageProcessor = VipsImageProcessor()

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

                AssetStreamContainer(imageChannel).use { container ->
                    container.toTemporaryFile()
                    val outputChannel = ByteChannel(true)
                    val outputBytesDeferred =
                        async {
                            outputChannel.toInputStream().readAllBytes()
                        }
                    var transformation = Transformation.ORIGINAL_VARIANT
                    Vips.run { arena ->
                        val sourceImage = VImage.newFromBytes(arena, image)
                        transformation =
                            Transformation(
                                width = sourceImage.width,
                                height = sourceImage.height,
                                format = ImageFormat.PNG,
                            )
                    }
                    val processedImage =
                        vipsImageProcessor.preprocess(
                            container = container,
                            sourceFormat = ImageFormat.PNG,
                            lqipImplementations = emptySet(),
                            transformation = transformation,
                            outputChannel = outputChannel,
                        )
                    val outputBytes = outputBytesDeferred.await()
                    processedImage.attributes.format shouldBe ImageFormat.PNG
                    processedImage.attributes.height shouldBe bufferedImage.height
                    processedImage.attributes.width shouldBe bufferedImage.width

                    ImageIO.read(ByteArrayInputStream(outputBytes)) shouldHaveSamePixelContentAs bufferedImage
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

        @Test
        fun `image lqips are not generated if not enabled when preprocessing`() =
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
                AssetStreamContainer(imageChannel).use { container ->
                    val outputChannel = ByteChannel(true)
                    val outputBytesDeferred =
                        async {
                            outputChannel.toInputStream().readAllBytes()
                        }
                    val processedImage =
                        vipsImageProcessor.preprocess(
                            container = container,
                            sourceFormat = Transformation.ORIGINAL_VARIANT.format,
                            lqipImplementations = emptySet(),
                            transformation = Transformation.ORIGINAL_VARIANT,
                            outputChannel = outputChannel,
                        )
                    val outputBytes = outputBytesDeferred.await()

                    processedImage.attributes.format shouldBe Transformation.ORIGINAL_VARIANT.format
                    Tika().detect(outputBytes) shouldBe Transformation.ORIGINAL_VARIANT.format.mimeType
                    processedImage.lqip.blurhash shouldBe null
                    processedImage.lqip.thumbhash shouldBe null
                }
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

                AssetStreamContainer(imageChannel).use { container ->
                    val outputChannel = ByteChannel(true)
                    val outputBytesDeferred =
                        async {
                            outputChannel.toInputStream().readAllBytes()
                        }
                    val processedImage =
                        vipsImageProcessor.preprocess(
                            container = container,
                            sourceFormat = ImageFormat.JPEG,
                            lqipImplementations = setOf(LQIPImplementation.BLURHASH),
                            transformation =
                                if (preprocessingEnabled) {
                                    Transformation(
                                        width = 200,
                                        height = 200,
                                        format = ImageFormat.JPEG,
                                    )
                                } else {
                                    Transformation.ORIGINAL_VARIANT
                                },
                            outputChannel = outputChannel,
                        )
                    val outputBytes = outputBytesDeferred.await()
                    processedImage.lqip.blurhash shouldNotBe null
                    processedImage.lqip.thumbhash shouldBe null
                    Vips.run { arena ->
                        val processedVImage = VImage.newFromBytes(arena, outputBytes)
                        shouldNotThrowAny {
                            BlurHash.decode(processedImage.lqip.blurhash!!, processedVImage.width, processedVImage.height)
                        }
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

            AssetStreamContainer(imageChannel).use { container ->
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage =
                    vipsImageProcessor.preprocess(
                        container = container,
                        sourceFormat = ImageFormat.JPEG,
                        lqipImplementations = setOf(LQIPImplementation.THUMBHASH),
                        transformation =
                            if (preprocessingEnabled) {
                                Transformation(
                                    width = 200,
                                    height = 200,
                                    format = ImageFormat.JPEG,
                                )
                            } else {
                                var transformation = Transformation.ORIGINAL_VARIANT
                                Vips.run { arena ->
                                    val sourceImage = VImage.newFromBytes(arena, image)
                                    transformation =
                                        Transformation(
                                            width = sourceImage.width,
                                            height = sourceImage.height,
                                            format = ImageFormat.JPEG,
                                        )
                                }
                                transformation
                            },
                        outputChannel = outputChannel,
                    )
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe ImageFormat.JPEG
                Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
                processedImage.lqip.blurhash shouldBe null
                processedImage.lqip.thumbhash shouldNotBe null

                shouldNotThrowAny {
                    ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(processedImage.lqip.thumbhash))
                }
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
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${from.extension}")!!.use {
                    it.readBytes()
                }
            val imageChannel = ByteChannel(true)
            launch {
                imageChannel.writeFully(image)
                imageChannel.close()
            }

            AssetStreamContainer(imageChannel).use { container ->
                container.toTemporaryFile()
                val outputChannel = ByteChannel(true)
                val outputBytesDeferred =
                    async {
                        outputChannel.toInputStream().readAllBytes()
                    }
                val processedImage =
                    vipsImageProcessor.preprocess(
                        container = container,
                        sourceFormat = from,
                        lqipImplementations = lqips,
                        transformation =
                            Transformation(
                                width = 100,
                                height = 100,
                                fit = Fit.FILL,
                                format = to,
                            ),
                        outputChannel = outputChannel,
                    )
                val outputBytes = outputBytesDeferred.await()

                processedImage.attributes.format shouldBe to
                Tika().detect(outputBytes) shouldBe to.mimeType
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    shouldNotThrowAny {
                        ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(processedImage.lqip.thumbhash))
                    }
                }
                Vips.run { arena ->
                    val processedVImage = VImage.newFromBytes(arena, outputBytes)

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
}
