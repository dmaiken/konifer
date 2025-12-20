package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import com.vanniktech.blurhash.BlurHash
import io.direkt.domain.asset.AssetDataContainer
import io.direkt.domain.image.Fit
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.variant.Transformation
import io.direkt.service.TemporaryFileFactory
import io.direkt.lqip.image.ThumbHash
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import io.matchers.shouldHaveSamePixelContentAs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.file.PathUtils.deleteOnExit
import org.apache.tika.Tika
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.readBytes

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

                AssetDataContainer(imageChannel).use { container ->
                    container.toTemporaryFile(".png")
                    val output = TemporaryFileFactory.createPreProcessedTempFile(ImageFormat.PNG.extension).apply {
                        deleteOnExit(this)
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
                            source = container.getTemporaryFile(),
                            sourceFormat = ImageFormat.PNG,
                            lqipImplementations = emptySet(),
                            transformation = transformation,
                            output = output,
                        )
                    val outputBytes = output.toFile().readBytes()
                    processedImage.attributes.format shouldBe ImageFormat.PNG
                    processedImage.attributes.height shouldBe bufferedImage.height
                    processedImage.attributes.width shouldBe bufferedImage.width

                    ImageIO.read(ByteArrayInputStream(outputBytes)) shouldHaveSamePixelContentAs bufferedImage
                }
            }

        @CartesianTest
        fun `formats can be converted to supported image formats`(
            @CartesianTest.Enum(ImageFormat::class) source: ImageFormat,
            @CartesianTest.Enum(ImageFormat::class) destination: ImageFormat,
        ) = runTest {
            testImageFormatConversion(source, destination)
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
                AssetDataContainer(imageChannel).use { container ->
                    container.toTemporaryFile(ImageFormat.JPEG.extension)
                    val output = TemporaryFileFactory.createPreProcessedTempFile(Transformation.ORIGINAL_VARIANT.format.extension).apply {
                        deleteOnExit(this)
                    }
                    val processedImage =
                        vipsImageProcessor.preprocess(
                            source = container.getTemporaryFile(),
                            sourceFormat = ImageFormat.JPEG,
                            lqipImplementations = emptySet(),
                            transformation = Transformation.ORIGINAL_VARIANT,
                            output = output
                        )
                    val outputBytes = output.readBytes()
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

                AssetDataContainer(imageChannel).use { container ->
                    container.toTemporaryFile(".jpeg")
                    val output = TemporaryFileFactory.createPreProcessedTempFile(ImageFormat.JPEG.extension).apply {
                        deleteOnExit(this)
                    }
                    val processedImage =
                        vipsImageProcessor.preprocess(
                            source = container.getTemporaryFile(),
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
                            output = output
                        )
                    val outputBytes = output.readBytes()
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

            AssetDataContainer(imageChannel).use { container ->
                container.toTemporaryFile(ImageFormat.JPEG.extension)
                val output = TemporaryFileFactory.createPreProcessedTempFile(ImageFormat.JPEG.extension).apply {
                    deleteOnExit(this)
                }
                val processedImage =
                    vipsImageProcessor.preprocess(
                        source = container.getTemporaryFile(),
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
                        output = output
                    )
                val outputBytes = output.readBytes()
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

            AssetDataContainer(imageChannel).use { container ->
                container.toTemporaryFile(from.extension)
                val output = TemporaryFileFactory.createPreProcessedTempFile(to.extension).apply {
                    deleteOnExit(this)
                }
                val processedImage =
                    vipsImageProcessor.preprocess(
                        source = container.getTemporaryFile(),
                        sourceFormat = from,
                        lqipImplementations = lqips,
                        transformation =
                            Transformation(
                                width = 100,
                                height = 100,
                                fit = Fit.FILL,
                                format = to,
                            ),
                        output = output
                    )
                val outputBytes = output.readBytes()

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
