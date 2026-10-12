package io.konifer.infrastructure.vips.processor

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import com.vanniktech.blurhash.BlurHash
import io.konifer.ImageFactory
import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.attribute.PixelAttributes
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_HEIGHT
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERPRETATION
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
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.io.ByteArrayInputStream
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.writeBytes

class VipsImageProcessorTest {
    private val vipsImageProcessor = VipsImageProcessor()

    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["RGBA", "BGRA", "ARGB", "ABGR"])
    fun `resizes stored planar RGBA pixels and encodes PNG`(channels: String) =
        runTest {
            val rgba =
                (0 until 16).map { pixel ->
                    listOf(pixel * 13, 255 - pixel * 7, pixel * 9, listOf(0, 64, 128, 255)[pixel % 4])
                }
            val planar = channels.flatMap { channel -> rgba.map { pixel -> pixel["RGBA".indexOf(channel)] } }
            val source = directory.resolve("source.bin").apply { writeBytes(planar.map(Int::toByte).toByteArray()) }
            val attributes =
                Attributes(
                    width = 4.toDimension(),
                    height = 4.toDimension(),
                    format = ImageFormat.PIXELS,
                    colorSpace = ColorSpace.SRGB,
                    pixels = PixelAttributes(channels.toPixelChannels(), PixelLayout.PLANAR, PixelDataType.UINT8),
                )
            val output =
                TransformationDataContainer(
                    transformation =
                        Transformation(
                            width = 2.toDimension(),
                            height = 2.toDimension(),
                            fit = Fit.STRETCH,
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                )
            lateinit var expected: ByteArray
            Vips.run { arena ->
                // Build the resize reference directly from canonical RGBA, bypassing raw decoding.
                expected =
                    VImage
                        .newFromMemory(
                            arena,
                            arena.allocateFrom(ValueLayout.JAVA_BYTE, *rgba.flatten().map(Int::toByte).toByteArray()),
                            4,
                            4,
                            4,
                            VipsBandFormat.FORMAT_UCHAR.rawValue,
                        ).copy(VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_sRGB))
                        .thumbnailImage(2, VipsOption.Int(OPTION_HEIGHT, 2))
                        .writeToMemory()
                        .toArray(ValueLayout.JAVA_BYTE)
            }

            try {
                vipsImageProcessor.generateVariants(
                    sourceFile = source,
                    sourceAttributes = attributes,
                    transformationDataContainers = listOf(output),
                    lqipImplementations = emptySet(),
                )
                output.output.close()
                val png = output.output.toByteArray()
                Tika().detect(png) shouldBe ImageFormat.PNG.mimeType
                val generatedAttributes = output.attributes.await()
                generatedAttributes.width shouldBe 2.toDimension()
                generatedAttributes.height shouldBe 2.toDimension()
                generatedAttributes.format shouldBe ImageFormat.PNG
                generatedAttributes.pixels shouldBe null
                output.lqips.await() shouldBe null

                Vips.run { arena ->
                    val decoded = VImage.newFromBytes(arena, png)
                    decoded.width shouldBe 2
                    decoded.height shouldBe 2
                    decoded.hasAlpha() shouldBe true
                    decoded.writeToMemory().toArray(ValueLayout.JAVA_BYTE).toList() shouldBe expected.toList()
                }
            } finally {
                output.output.cancel(null)
            }
        }

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
                                width = 300.toDimension(),
                                height = 200.toDimension(),
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
                attributes.width shouldBe 300.toDimension()
                attributes.height shouldBe 200.toDimension()
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
                                            width = sourceImage.width.toDimension(),
                                            height = sourceImage.height.toDimension(),
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
                            attributes.height shouldBe bufferedImage.height.toDimension()
                            attributes.width shouldBe bufferedImage.width.toDimension()
                        }
                    }
                }
            }

        @CartesianTest
        fun `formats can be converted to supported image formats`(
            @CartesianTest.Enum(ImageFormat::class, mode = CartesianTest.Enum.Mode.EXCLUDE, names = ["PIXELS"]) source: ImageFormat,
            @CartesianTest.Enum(ImageFormat::class, mode = CartesianTest.Enum.Mode.EXCLUDE, names = ["PIXELS"]) destination: ImageFormat,
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
                            transformation =
                                Transformation(
                                    width = 1.toDimension(),
                                    height = 1.toDimension(),
                                    format = ImageFormat.PNG,
                                    colorSpace = ColorSpace.SRGB,
                                ),
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
                    attributes.format shouldBe ImageFormat.PNG
                    Tika().detect(outputBytes) shouldBe ImageFormat.PNG.mimeType
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
                                    width = 200.toDimension(),
                                    height = 200.toDimension(),
                                    format = ImageFormat.JPEG,
                                    colorSpace = ColorSpace.SRGB,
                                )
                            } else {
                                Transformation(
                                    width = image.attributes.width.toDimension(),
                                    height = image.attributes.height.toDimension(),
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
                                width = 200.toDimension(),
                                height = 200.toDimension(),
                                format = image.attributes.format,
                                colorSpace = ColorSpace.SRGB,
                            )
                        } else {
                            Transformation(
                                width = image.attributes.width.toDimension(),
                                height = image.attributes.height.toDimension(),
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
        @MethodSource("io.konifer.ImageTestSources#supportedInputSources")
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
                                width = 100.toDimension(),
                                height = 100.toDimension(),
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

                    attributes.height shouldBe processedVImage.height.toDimension()
                    attributes.width shouldBe processedVImage.width.toDimension()

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
