package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.attribute.PixelAttributes
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.encode.RawPixelEncoder
import io.konifer.infrastructure.vips.format
import io.konifer.infrastructure.vips.interpretation
import io.konifer.infrastructure.vips.transformer.PixelAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import kotlin.io.path.writeBytes

class RawPixelDecoderTest {
    @TempDir
    lateinit var directory: Path

    companion object {
        @JvmStatic
        fun layouts() =
            listOf(
                arguments("RGB", listOf(10, 20, 30, 128, 200, 255), listOf(10, 20, 30, 128, 200, 255)),
                arguments("BGR", listOf(30, 20, 10, 255, 200, 128), listOf(10, 20, 30, 128, 200, 255)),
                arguments("GRB", listOf(20, 10, 30, 200, 128, 255), listOf(10, 20, 30, 128, 200, 255)),
                arguments("RGBA", listOf(10, 20, 30, 0, 128, 200, 255, 127), listOf(10, 20, 30, 0, 128, 200, 255, 127)),
                arguments("BGRA", listOf(30, 20, 10, 0, 255, 200, 128, 127), listOf(10, 20, 30, 0, 128, 200, 255, 127)),
                arguments("ARGB", listOf(0, 10, 20, 30, 127, 128, 200, 255), listOf(10, 20, 30, 0, 128, 200, 255, 127)),
                arguments("ABGR", listOf(0, 30, 20, 10, 127, 255, 200, 128), listOf(10, 20, 30, 0, 128, 200, 255, 127)),
                arguments("GARB", listOf(20, 0, 10, 30, 200, 127, 128, 255), listOf(10, 20, 30, 0, 128, 200, 255, 127)),
            )

        @JvmStatic
        fun planarLayouts() =
            listOf(
                arguments("RGB", listOf(10, 128, 40, 70, 20, 200, 50, 80, 30, 255, 60, 90)),
                arguments("BGR", listOf(30, 255, 60, 90, 20, 200, 50, 80, 10, 128, 40, 70)),
                arguments("GRB", listOf(20, 200, 50, 80, 10, 128, 40, 70, 30, 255, 60, 90)),
                arguments("RGBA", listOf(10, 128, 40, 70, 20, 200, 50, 80, 30, 255, 60, 90, 0, 127, 192, 255)),
                arguments("BGRA", listOf(30, 255, 60, 90, 20, 200, 50, 80, 10, 128, 40, 70, 0, 127, 192, 255)),
                arguments("ARGB", listOf(0, 127, 192, 255, 10, 128, 40, 70, 20, 200, 50, 80, 30, 255, 60, 90)),
                arguments("ABGR", listOf(0, 127, 192, 255, 30, 255, 60, 90, 20, 200, 50, 80, 10, 128, 40, 70)),
                arguments("GARB", listOf(20, 200, 50, 80, 0, 127, 192, 255, 10, 128, 40, 70, 30, 255, 60, 90)),
            )
    }

    @ParameterizedTest
    @MethodSource("planarLayouts")
    fun `decodes planar channels across rows and round trips through either output layout`(
        channels: String,
        pixels: List<Int>,
    ) = runTest {
        val source = directory.resolve("planar.bin").apply { writeBytes(pixels.map(Int::toByte).toByteArray()) }
        Arena.ofConfined().use { arena ->
            val image =
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PIXELS,
                    ImageFormat.PIXELS,
                    source,
                    attributes(channels, PixelLayout.PLANAR).copy(height = 2.toDimension(), orientation = 6),
                )
            val canonicalChannels = if (channels.length == 4) "RGBA" else "RGB"
            val expected =
                if (channels.length == 4) {
                    listOf(10, 20, 30, 0, 128, 200, 255, 127, 40, 50, 60, 192, 70, 80, 90, 255)
                } else {
                    listOf(10, 20, 30, 128, 200, 255, 40, 50, 60, 70, 80, 90)
                }
            image.width shouldBe 2
            image.height shouldBe 2
            image.getInt("bands") shouldBe channels.length
            image.getInt("orientation") shouldBe 6
            image.format() shouldBe VipsBandFormat.FORMAT_UCHAR
            ImageColorSpaceExtractor.extract(image) shouldBe ColorSpace.SRGB
            repeat(2) {
                image.writeToMemory().toArray(ValueLayout.JAVA_BYTE).toList() shouldBe expected.map(Int::toByte)
            }
            PixelLayout.entries.forEach { layout ->
                val output = ByteChannel(autoFlush = true)
                try {
                    RawPixelEncoder.writeToStream(
                        arena,
                        image,
                        Transformation(
                            width = 2.toDimension(),
                            height = 2.toDimension(),
                            format = ImageFormat.PIXELS,
                            colorSpace = ColorSpace.SRGB,
                            pixels = PixelTransformation(channels.toPixelChannels(), layout),
                        ),
                        output,
                    )
                    output.close()
                    val expectedOutput =
                        when (layout) {
                            PixelLayout.PLANAR -> {
                                pixels
                            }

                            PixelLayout.INTERLEAVED -> {
                                expected.chunked(channels.length).flatMap { pixel ->
                                    channels.map { channel -> pixel[canonicalChannels.indexOf(channel)] }
                                }
                            }
                        }
                    output.readRemaining().readByteArray().toList() shouldBe expectedOutput.map(Int::toByte)
                } finally {
                    output.cancel(null)
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("layouts")
    fun `decodes channel layouts into canonical RGB or RGBA`(
        layout: String,
        pixels: List<Int>,
        expected: List<Int>,
    ) {
        val source = directory.resolve("source.bin").apply { writeBytes(pixels.map(Int::toByte).toByteArray()) }
        Vips.run { arena ->
            val image =
                VipsDecoderSelector.getDecoder(ImageFormat.PIXELS).decodeSource(
                    arena = arena,
                    destinationFormat = ImageFormat.PNG,
                    sourceFormat = ImageFormat.PIXELS,
                    source = source,
                    sourceAttributes = attributes(layout),
                )
            image.width shouldBe 2
            image.height shouldBe 1
            image.getInt("bands") shouldBe expected.size / 2
            ImageColorSpaceExtractor.extract(image) shouldBe ColorSpace.SRGB
            repeat(2) {
                val buffer = image.writeToMemory().asByteBuffer()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes.map { it.toInt() and 0xff } shouldBe expected
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `uses stored dimensions and preserves rows without relying on the filename extension`(layout: PixelLayout) {
        val pixels =
            when (layout) {
                PixelLayout.INTERLEAVED -> listOf(30, 20, 10, 255, 200, 128, 60, 50, 40, 90, 80, 70)
                PixelLayout.PLANAR -> listOf(30, 255, 60, 90, 20, 200, 50, 80, 10, 128, 40, 70)
            }
        val source = directory.resolve("source").apply { writeBytes(pixels.map(Int::toByte).toByteArray()) }
        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    transformation =
                        Transformation(
                            width = 1.toDimension(),
                            height = 1.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                        ),
                    sourceFormat = ImageFormat.PIXELS,
                    sourceFile = source,
                    sourceAttributes = attributes("BGR", layout).copy(height = 2.toDimension()),
                )
            decoded.pixelAccess shouldBe PixelAccess.RANDOM
            decoded.appliedTransformations shouldBe emptyList()
            decoded.image.width shouldBe 2
            decoded.image.height shouldBe 2
            val buffer = decoded.image.writeToMemory().asByteBuffer()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes.map { it.toInt() and 0xff } shouldBe listOf(10, 20, 30, 128, 200, 255, 40, 50, 60, 70, 80, 90)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["GRAY", "gray", "gRaY"])
    fun `decodes grayscale as one unsigned byte per pixel and preserves rows`(layout: String) {
        val pixels = listOf(0, 64, 128, 255).map(Int::toByte).toByteArray()
        val source = directory.resolve("grayscale").apply { writeBytes(pixels) }
        Vips.run { arena ->
            val decoded =
                VipsThumbnailDecoder.decode(
                    arena = arena,
                    transformation =
                        Transformation(
                            width = 1.toDimension(),
                            height = 1.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.Grayscale,
                        ),
                    sourceFormat = ImageFormat.PIXELS,
                    sourceFile = source,
                    sourceAttributes = attributes(layout).copy(height = 2.toDimension()),
                )
            val image = decoded.image
            decoded.pixelAccess shouldBe PixelAccess.RANDOM
            decoded.appliedTransformations shouldBe emptyList()
            image.width shouldBe 2
            image.height shouldBe 2
            image.getInt("bands") shouldBe 1
            image.format() shouldBe VipsBandFormat.FORMAT_UCHAR
            image.interpretation() shouldBe VipsInterpretation.INTERPRETATION_B_W.rawValue
            image.hasAlpha() shouldBe false
            ImageColorSpaceExtractor.extract(image) shouldBe ColorSpace.Grayscale
            image.getBlob("icc-profile-data") shouldBe null
            repeat(2) {
                val buffer = image.writeToMemory().asByteBuffer()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes.toList() shouldBe pixels.toList()
            }
        }
    }

    @Test
    fun `grayscale pixels round trip through the raw encoder`() =
        runTest {
            val pixels = listOf(0, 64, 128, 255).map(Int::toByte).toByteArray()
            val source = directory.resolve("grayscale.bin").apply { writeBytes(pixels) }
            val output = ByteChannel(autoFlush = true)
            try {
                Vips.run { arena ->
                    val image =
                        RawPixelDecoder.decodeSource(
                            arena,
                            ImageFormat.PIXELS,
                            ImageFormat.PIXELS,
                            source,
                            attributes("GRAY").copy(height = 2.toDimension()),
                        )
                    RawPixelEncoder.writeToStream(
                        arena,
                        image,
                        Transformation(
                            width = 2.toDimension(),
                            height = 2.toDimension(),
                            format = ImageFormat.PIXELS,
                            colorSpace = ColorSpace.Grayscale,
                            pixels = PixelTransformation(PixelChannels.grayscale),
                        ),
                        output,
                    )
                    output.close()
                }
                output.readRemaining().readByteArray().toList() shouldBe pixels.toList()
            } finally {
                output.cancel(null)
            }
        }

    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `preserves P3 samples and attaches the stored color profile`(layout: PixelLayout) {
        val expected = listOf(10, 20, 30, 128, 200, 255).map(Int::toByte)
        val pixels =
            when (layout) {
                PixelLayout.INTERLEAVED -> expected.toByteArray()
                PixelLayout.PLANAR -> listOf(10, 128, 20, 200, 30, 255).map(Int::toByte).toByteArray()
            }
        val source = directory.resolve("source.bin").apply { writeBytes(pixels) }
        Vips.run { arena ->
            val image =
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PNG,
                    ImageFormat.PIXELS,
                    source,
                    attributes("RGB", layout).copy(colorSpace = ColorSpace.P3),
                )
            ImageColorSpaceExtractor.extract(image) shouldBe ColorSpace.P3
            val buffer = image.writeToMemory().asByteBuffer()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes.toList() shouldBe expected
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "GRAY"])
    fun `restores orientation metadata for subsequent auto rotation`(layout: String) {
        val source =
            directory.resolve("source.bin").apply {
                val pixels = if (layout == "GRAY") listOf(10, 128) else listOf(10, 20, 30, 128, 200, 255)
                writeBytes(pixels.map(Int::toByte).toByteArray())
            }
        Vips.run { arena ->
            val image =
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PNG,
                    ImageFormat.PIXELS,
                    source,
                    attributes(layout).copy(orientation = 6),
                )
            image.getInt("orientation") shouldBe 6
            val rotated = image.autorot()
            rotated.width shouldBe 1
            rotated.height shouldBe 2
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 5, 7])
    fun `rejects truncated and oversized payloads`(size: Int) {
        val source = directory.resolve("source.bin").apply { writeBytes(ByteArray(size)) }
        Vips.run { arena ->
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(arena, ImageFormat.PNG, ImageFormat.PIXELS, source, attributes("RGB"))
            }.message shouldBe "Raw pixel size mismatch: expected 6 bytes for 2x1 RGB, found $size"
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 3, 8])
    fun `validates grayscale payload size using one channel`(size: Int) {
        val source = directory.resolve("grayscale.bin").apply { writeBytes(ByteArray(size)) }
        Vips.run { arena ->
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(arena, ImageFormat.PNG, ImageFormat.PIXELS, source, attributes("GRAY"))
            }.message shouldBe "Raw pixel size mismatch: expected 2 bytes for 2x1 GRAY, found $size"
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["srgb", "p3", "unknown"])
    fun `rejects grayscale channels with conflicting color metadata`(colorSpace: String) {
        val source = directory.resolve("grayscale.bin").apply { writeBytes(ByteArray(2)) }
        Vips.run { arena ->
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PNG,
                    ImageFormat.PIXELS,
                    source,
                    attributes("GRAY").copy(colorSpace = colorSpace.toColorSpace()),
                )
            }.message shouldBe "Raw pixel channels GRAY require grayscale color space, found $colorSpace"
        }
    }

    @Test
    fun `rejects missing source or channel metadata`() {
        val source = directory.resolve("source.bin").apply { writeBytes(ByteArray(6)) }
        Vips.run { arena ->
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(arena, ImageFormat.PNG, ImageFormat.PIXELS, source)
            }.message shouldBe "Raw pixels require stored source attributes"
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(arena, ImageFormat.PNG, ImageFormat.PIXELS, source, attributes("RGB").copy(pixels = null))
            }.message shouldBe "Raw pixels require stored channel layout"
        }
    }

    @Test
    fun `encoded formats still select the file decoder`() {
        VipsDecoderSelector.getDecoder(ImageFormat.PNG) shouldBe VipsFileDecoder
        VipsDecoderSelector.getDecoder(ImageFormat.JPEG) shouldBe VipsFileDecoder
    }

    @Test
    fun `rejects metadata whose format or color profile cannot describe the raw source`() {
        val source = directory.resolve("source.bin").apply { writeBytes(ByteArray(6)) }
        Vips.run { arena ->
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PNG,
                    ImageFormat.PIXELS,
                    source,
                    attributes("RGB").copy(format = ImageFormat.PNG),
                )
            }.message shouldBe "Raw pixel attributes must describe PIXELS format"
            shouldThrow<IllegalArgumentException> {
                RawPixelDecoder.decodeSource(
                    arena,
                    ImageFormat.PNG,
                    ImageFormat.PIXELS,
                    source,
                    attributes("RGB").copy(colorSpace = ColorSpace.Unknown),
                )
            }.message shouldBe "Unsupported raw pixel color space: unknown"
        }
    }

    private fun attributes(
        layout: String,
        pixelLayout: PixelLayout = PixelLayout.INTERLEAVED,
    ) = Attributes(
        width = 2.toDimension(),
        height = 1.toDimension(),
        format = ImageFormat.PIXELS,
        colorSpace = if (layout.toPixelChannels() == PixelChannels.grayscale) ColorSpace.Grayscale else ColorSpace.SRGB,
        pixels =
            PixelAttributes(
                channels = layout.toPixelChannels(),
                layout = pixelLayout,
                dataType = PixelDataType.UINT8,
            ),
    )
}
