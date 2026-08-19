package io.konifer.infrastructure.vips.processor

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import com.vanniktech.blurhash.BlurHash
import io.konifer.ImageFactory
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.decode.DecodedVipsImage
import io.konifer.lqip.image.ThumbHash
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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

class VipsImageProcessorTest {
    private val vipsImageProcessor = VipsImageProcessor()

    @Nested
    inner class PreProcessTests {
        @ParameterizedTest
        @EnumSource(Rotate::class, names = ["NINETY", "TWO_HUNDRED_SEVENTY"])
        fun `rotation is applied before resize so requested dimensions describe the output`(rotate: Rotate) =
            runTest {
                val image = ImageFactory.testImage()
                val transformationDataContainer =
                    TransformationDataContainer(
                        transformation =
                            Transformation(
                                width = 300,
                                height = 200,
                                fit = Fit.STRETCH,
                                rotate = rotate,
                                format = ImageFormat.JPEG,
                                colorSpace = ColorSpace.SRGB,
                            ),
                    )

                Vips.run { arena ->
                    vipsImageProcessor.preprocess(
                        arena = arena,
                        source = DecodedVipsImage(VImage.newFromBytes(arena, image.bytes)),
                        sourceFormat = ImageFormat.JPEG,
                        lqipImplementations = emptySet(),
                        transformationDataContainer = transformationDataContainer,
                    ) shouldBe PreprocessOutput.SourceTransformed
                }

                val attributes = transformationDataContainer.attributes.await()
                attributes.width shouldBe 300
                attributes.height shouldBe 200
            }

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
                    container.toTemporaryFile(ImageFormat.PNG.extension)
                    Vips.run { arena ->
                        runBlocking {
                            val sourceImage = VImage.newFromBytes(arena, image)
                            val transformationDataContainer =
                                TransformationDataContainer(
                                    transformation =
                                        Transformation(
                                            width = sourceImage.width,
                                            height = sourceImage.height,
                                            format = ImageFormat.PNG,
                                            colorSpace = ColorSpace.SRGB,
                                        ),
                                )
                            launch {
                                vipsImageProcessor.preprocess(
                                    arena = arena,
                                    source = DecodedVipsImage(sourceImage),
                                    sourceFormat = ImageFormat.PNG,
                                    lqipImplementations = emptySet(),
                                    transformationDataContainer = transformationDataContainer,
                                ) shouldBe PreprocessOutput.SourceNotTransformed
                            }
                            val attributes = transformationDataContainer.attributes.await()
                            attributes.format shouldBe ImageFormat.PNG
                            attributes.height shouldBe bufferedImage.height
                            attributes.width shouldBe bufferedImage.width
                        }
                    }
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
                    val transformationDataContainer =
                        TransformationDataContainer(
                            transformation = Transformation.ORIGINAL_VARIANT,
                        )
                    container.toTemporaryFile(ImageFormat.JPEG.extension)
                    Vips.run { arena ->
                        val sourceImage = VImage.newFromBytes(arena, image)
                        vipsImageProcessor.preprocess(
                            arena = arena,
                            source = DecodedVipsImage(sourceImage),
                            sourceFormat = ImageFormat.JPEG,
                            lqipImplementations = emptySet(),
                            transformationDataContainer = transformationDataContainer,
                        ) shouldBe PreprocessOutput.SourceTransformed
                    }

                    val outputBytes = transformationDataContainer.output.toByteArray()
                    val attributes = transformationDataContainer.attributes.await()
                    val lqips = transformationDataContainer.lqips.await()
                    attributes.format shouldBe Transformation.ORIGINAL_VARIANT.format
                    Tika().detect(outputBytes) shouldBe Transformation.ORIGINAL_VARIANT.format.mimeType
                    lqips?.blurhash shouldBe null
                    lqips?.thumbhash shouldBe null
                }
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `image blurhash is generated regardless of whether preprocessing is enabled when preprocessing`(preprocessingEnabled: Boolean) =
            runTest {
                val image = ImageFactory.testImage()

                val transformationDataContainer =
                    TransformationDataContainer(
                        transformation =
                            if (preprocessingEnabled) {
                                Transformation(
                                    width = 200,
                                    height = 200,
                                    format = ImageFormat.JPEG,
                                    colorSpace = ColorSpace.SRGB,
                                )
                            } else {
                                Transformation(
                                    width = image.attributes.width,
                                    height = image.attributes.height,
                                    format = image.attributes.format,
                                    colorSpace = ColorSpace.SRGB,
                                )
                            },
                    )
                Vips.run { arena ->
                    val output =
                        vipsImageProcessor.preprocess(
                            arena = arena,
                            source = DecodedVipsImage(VImage.newFromBytes(arena, image.bytes)),
                            sourceFormat = ImageFormat.JPEG,
                            lqipImplementations = setOf(LQIPImplementation.BLURHASH),
                            transformationDataContainer = transformationDataContainer,
                        )
                    when (preprocessingEnabled) {
                        true -> output shouldBe PreprocessOutput.SourceTransformed
                        false -> output shouldBe PreprocessOutput.SourceNotTransformed
                    }
                }

                val lqips = transformationDataContainer.lqips.await()
                lqips?.blurhash shouldNotBe null
                lqips?.thumbhash shouldBe null
                if (preprocessingEnabled) {
                    val output = transformationDataContainer.output.toByteArray()
                    Vips.run { arena ->
                        val processedVImage = VImage.newFromBytes(arena, output)
                        shouldNotThrowAny {
                            BlurHash.decode(lqips?.blurhash!!, processedVImage.width, processedVImage.height)
                        }
                    }
                } else {
                    Vips.run { arena ->
                        val processedVImage = VImage.newFromBytes(arena, image.bytes)
                        shouldNotThrowAny {
                            BlurHash.decode(lqips?.blurhash!!, processedVImage.width, processedVImage.height)
                        }
                    }
                }
            }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `image thumbhash is generated regardless of whether preprocessing is enabled when preprocessing`(
            preprocessingEnabled: Boolean,
        ) = runTest {
            val image = ImageFactory.testImage()

            val transformationDataContainer =
                TransformationDataContainer(
                    transformation =
                        if (preprocessingEnabled) {
                            Transformation(
                                width = 200,
                                height = 200,
                                format = image.attributes.format,
                                colorSpace = ColorSpace.SRGB,
                            )
                        } else {
                            Transformation(
                                width = image.attributes.width,
                                height = image.attributes.height,
                                format = image.attributes.format,
                                colorSpace = ColorSpace.SRGB,
                            )
                        },
                )
            Vips.run { arena ->
                val output =
                    vipsImageProcessor.preprocess(
                        arena = arena,
                        source = DecodedVipsImage(VImage.newFromBytes(arena, image.bytes)),
                        sourceFormat = ImageFormat.JPEG,
                        lqipImplementations = setOf(LQIPImplementation.THUMBHASH),
                        transformationDataContainer = transformationDataContainer,
                    )
                when (preprocessingEnabled) {
                    true -> output shouldBe PreprocessOutput.SourceTransformed
                    false -> output shouldBe PreprocessOutput.SourceNotTransformed
                }
            }
            if (preprocessingEnabled) {
                val outputBytes = transformationDataContainer.output.toByteArray()
                val attributes = transformationDataContainer.attributes.await()
                attributes.format shouldBe ImageFormat.JPEG
                Tika().detect(outputBytes) shouldBe ImageFormat.JPEG.mimeType
            }

            val lqips = transformationDataContainer.lqips.await()
            lqips?.blurhash shouldBe null
            lqips?.thumbhash shouldNotBe null

            shouldNotThrowAny {
                ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(lqips?.thumbhash))
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
                val transformationDataContainer =
                    TransformationDataContainer(
                        transformation =
                            Transformation(
                                width = 100,
                                height = 100,
                                fit = Fit.FILL,
                                format = to,
                                colorSpace = ColorSpace.SRGB,
                            ),
                    )
                Vips.run { arena ->
                    vipsImageProcessor.preprocess(
                        arena = arena,
                        source = DecodedVipsImage(VImage.newFromBytes(arena, image)),
                        sourceFormat = from,
                        lqipImplementations = lqips,
                        transformationDataContainer = transformationDataContainer,
                    ) shouldBe PreprocessOutput.SourceTransformed
                }
                val outputBytes = transformationDataContainer.output.toByteArray()

                val attributes = transformationDataContainer.attributes.await()
                val generatedLqips = transformationDataContainer.lqips.await()
                attributes.format shouldBe to
                Tika().detect(outputBytes) shouldBe to.mimeType
                if (lqips.contains(LQIPImplementation.THUMBHASH)) {
                    shouldNotThrowAny {
                        ThumbHash.thumbHashToRGBA(Base64.getDecoder().decode(generatedLqips?.thumbhash))
                    }
                }
                Vips.run { arena ->
                    val processedVImage = VImage.newFromBytes(arena, outputBytes)

                    attributes.height shouldBe processedVImage.height
                    attributes.width shouldBe processedVImage.width

                    if (lqips.contains(LQIPImplementation.BLURHASH)) {
                        shouldNotThrowAny {
                            BlurHash.decode(generatedLqips?.blurhash!!, processedVImage.width, processedVImage.height)
                        }
                    }
                }
            }
        }
    }
}
