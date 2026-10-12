package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ACCESS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERPRETATION
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout

class RawPixelEncoderTest {
    companion object {
        @JvmStatic
        fun pixelLayouts() =
            listOf(
                arguments(3, "RGB", listOf(10, 20, 30, 128, 200, 255)),
                arguments(3, "BGR", listOf(30, 20, 10, 255, 200, 128)),
                arguments(3, "RGBA", listOf(10, 20, 30, 255, 128, 200, 255, 255)),
                arguments(3, "BGRA", listOf(30, 20, 10, 255, 255, 200, 128, 255)),
                arguments(3, "ARGB", listOf(255, 10, 20, 30, 255, 128, 200, 255)),
                arguments(4, "RGBA", listOf(10, 20, 30, 0, 128, 200, 255, 127)),
                arguments(4, "RGB", listOf(10, 20, 30, 128, 200, 255)),
                arguments(4, "BGR", listOf(30, 20, 10, 255, 200, 128)),
                arguments(4, "BGRA", listOf(30, 20, 10, 0, 255, 200, 128, 127)),
                arguments(4, "ARGB", listOf(0, 10, 20, 30, 127, 128, 200, 255)),
                arguments(4, "ABGR", listOf(0, 30, 20, 10, 127, 255, 200, 128)),
            )

        @JvmStatic
        fun planarPixelLayouts() =
            listOf(
                arguments(3, "RGB", listOf(10, 128, 20, 200, 30, 255)),
                arguments(3, "BGR", listOf(30, 255, 20, 200, 10, 128)),
                arguments(3, "RGBA", listOf(10, 128, 20, 200, 30, 255, 255, 255)),
                arguments(3, "BGRA", listOf(30, 255, 20, 200, 10, 128, 255, 255)),
                arguments(3, "ARGB", listOf(255, 255, 10, 128, 20, 200, 30, 255)),
                arguments(3, "ABGR", listOf(255, 255, 30, 255, 20, 200, 10, 128)),
                arguments(4, "RGBA", listOf(10, 128, 20, 200, 30, 255, 0, 127)),
                arguments(4, "RGB", listOf(10, 128, 20, 200, 30, 255)),
                arguments(4, "BGR", listOf(30, 255, 20, 200, 10, 128)),
                arguments(4, "BGRA", listOf(30, 255, 20, 200, 10, 128, 0, 127)),
                arguments(4, "ARGB", listOf(0, 127, 10, 128, 20, 200, 30, 255)),
                arguments(4, "ABGR", listOf(0, 127, 30, 255, 20, 200, 10, 128)),
            )
    }

    @ParameterizedTest
    @MethodSource("planarPixelLayouts")
    fun `writes complete planes in requested channel order`(
        bands: Int,
        channels: String,
        expected: List<Int>,
    ) = runTest {
        Arena.ofConfined().use { arena ->
            val pixels =
                if (bands == 3) {
                    listOf(10, 20, 30, 128, 200, 255)
                } else {
                    listOf(10, 20, 30, 0, 128, 200, 255, 127)
                }
            val source =
                VImage.newFromMemory(
                    arena,
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels.map(Int::toByte).toByteArray()),
                    2,
                    1,
                    bands,
                    VipsBandFormat.FORMAT_UCHAR.rawValue,
                )

            encode(arena, source, transformation(channels, PixelLayout.PLANAR)).toList() shouldBe expected.map(Int::toByte)
        }
    }

    @ParameterizedTest
    @MethodSource("pixelLayouts")
    fun `writes interleaved bytes in requested layout`(
        bands: Int,
        layout: String,
        expected: List<Int>,
    ) = runTest {
        Arena.ofConfined().use { arena ->
            val pixels =
                if (bands == 3) {
                    listOf(10, 20, 30, 128, 200, 255)
                } else {
                    listOf(10, 20, 30, 0, 128, 200, 255, 127)
                }
            val source =
                VImage.newFromMemory(
                    arena,
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels.map(Int::toByte).toByteArray()),
                    2,
                    1,
                    bands,
                    VipsBandFormat.FORMAT_UCHAR.rawValue,
                )

            encode(arena, source, transformation(layout)).toList() shouldBe expected.map(Int::toByte)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "BGRA", "GRAY"])
    fun `rejects samples that were not cast by the pipeline`(layout: String) {
        Arena.ofConfined().use { arena ->
            val bands = if (layout == "GRAY") 1 else 3
            val source =
                VImage.newFromMemory(
                    arena,
                    arena.allocateFrom(ValueLayout.JAVA_FLOAT, *FloatArray(2 * bands) { 128.75f }),
                    2,
                    1,
                    bands,
                    VipsBandFormat.FORMAT_FLOAT.rawValue,
                )

            shouldThrow<IllegalArgumentException> {
                RawPixelEncoder.writeToStream(arena, source, transformation(layout), ByteChannel())
            }.message shouldBe "Source pixels not in correct datatype, were they casted? Source datatype: FORMAT_FLOAT"
        }
    }

    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `writes multiple rows without headers or padding`(layout: PixelLayout) =
        runTest {
            Arena.ofConfined().use { arena ->
                val pixels = (0..11).map(Int::toByte).toByteArray()
                val source =
                    VImage.newFromMemory(
                        arena,
                        arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                        2,
                        2,
                        3,
                        VipsBandFormat.FORMAT_UCHAR.rawValue,
                    )

                val expected =
                    when (layout) {
                        PixelLayout.INTERLEAVED -> pixels.toList()
                        PixelLayout.PLANAR -> listOf(0, 3, 6, 9, 1, 4, 7, 10, 2, 5, 8, 11).map(Int::toByte)
                    }

                encode(arena, source, transformation("RGB", layout).copy(height = 2.toDimension())).toList() shouldBe expected
            }
        }

    @ParameterizedTest
    @ValueSource(ints = [1, 3])
    fun `writes one unsigned byte per grayscale pixel across multiple rows`(bands: Int) =
        runTest {
            Arena.ofConfined().use { arena ->
                val intensities = listOf(0, 64, 128, 255)
                val pixels = intensities.flatMap { intensity -> List(bands) { intensity.toByte() } }.toByteArray()
                val source =
                    VImage
                        .newFromMemory(
                            arena,
                            arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                            2,
                            2,
                            bands,
                            VipsBandFormat.FORMAT_UCHAR.rawValue,
                        ).copy(
                            VipsOption.Enum(
                                OPTION_INTERPRETATION,
                                if (bands == 1) VipsInterpretation.INTERPRETATION_B_W else VipsInterpretation.INTERPRETATION_sRGB,
                            ),
                        )

                PixelLayout.entries.forEach { layout ->
                    val output = encode(arena, source, transformation("GRAY", layout).copy(height = 2.toDimension()))

                    output.size shouldBe 4
                    output.toList() shouldBe intensities.map(Int::toByte)
                }
            }
        }

    @Test
    fun `converts RGB colors to grayscale intensity`() =
        runTest {
            Arena.ofConfined().use { arena ->
                val pixels = listOf(255, 0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0).map(Int::toByte).toByteArray()
                val source =
                    VImage
                        .newFromMemory(
                            arena,
                            arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                            2,
                            2,
                            3,
                            VipsBandFormat.FORMAT_UCHAR.rawValue,
                        ).copy(VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_sRGB))

                val output =
                    encode(arena, source, transformation("GRAY").copy(height = 2.toDimension()))
                        .map { it.toInt() and 0xff }

                output.size shouldBe 4
                // Grayscale conversion combines all three colors instead of selecting a single band.
                (output[0] in 120..130) shouldBe true
                (output[1] in 215..225) shouldBe true
                (output[2] in 70..80) shouldBe true
                output[3] shouldBe 0
            }
        }

    @ParameterizedTest
    @ValueSource(ints = [2, 4])
    fun `flattens grayscale alpha against white before writing one channel`(bands: Int) =
        runTest {
            Arena.ofConfined().use { arena ->
                val pixels =
                    listOf(0 to 0, 0 to 128, 0 to 255, 255 to 0)
                        .flatMap { (intensity, alpha) -> List(bands - 1) { intensity.toByte() } + alpha.toByte() }
                        .toByteArray()
                val source =
                    VImage
                        .newFromMemory(
                            arena,
                            arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                            2,
                            2,
                            bands,
                            VipsBandFormat.FORMAT_UCHAR.rawValue,
                        ).copy(
                            VipsOption.Enum(
                                OPTION_INTERPRETATION,
                                if (bands == 2) VipsInterpretation.INTERPRETATION_B_W else VipsInterpretation.INTERPRETATION_sRGB,
                            ),
                        )

                val output = encode(arena, source, transformation("GRAY").copy(height = 2.toDimension()))

                output.size shouldBe 4
                output.toList() shouldBe listOf(255, 127, 0, 255).map(Int::toByte)
            }
        }

    @Test
    fun `rejects transformation without pixel channels`() {
        Vips.run { arena ->
            val source = VImage.black(arena, 2, 1).bandjoinConst(listOf(0.0, 0.0))

            shouldThrow<IllegalStateException> {
                RawPixelEncoder.writeToStream(arena, source, transformation("RGB").copy(pixels = null), ByteChannel())
            }.message shouldBe "Pixel channels missing despite format of pixels"
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 5])
    fun `rejects images without three or four bands`(bands: Int) {
        Vips.run { arena ->
            val source =
                VImage.newFromMemory(
                    arena,
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, *ByteArray(2 * bands)),
                    2,
                    1,
                    bands,
                    VipsBandFormat.FORMAT_UCHAR.rawValue,
                )

            shouldThrow<IllegalArgumentException> {
                RawPixelEncoder.writeToStream(arena, source, transformation("RGB"), ByteChannel())
            }.message shouldBe "Expected 3 or 4 RGB bands when normalizing image tensor but found $bands bands"
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "BGR", "RGBA", "BGRA", "ARGB", "ABGR"])
    fun `writes planar output from a sequential decoder beyond its scanline cache`(channels: String) =
        runTest {
            Arena.ofConfined().use { arena ->
                val width = 17
                val height = 2049
                val pixels = ByteArray(width * height * 3) { (it % 251).toByte() }
                val image =
                    VImage.newFromMemory(
                        arena,
                        arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                        width,
                        height,
                        3,
                        VipsBandFormat.FORMAT_UCHAR.rawValue,
                    )
                val png = ByteArrayOutputStream()
                image.writeToStream(png, ".png")
                val source =
                    VImage.newFromBytes(
                        arena,
                        png.toByteArray(),
                        VipsOption.Enum(OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
                    )
                val expected =
                    channels.flatMap { channel ->
                        if (channel == 'A') {
                            List(width * height) { 255.toByte() }
                        } else {
                            val band = "RGB".indexOf(channel)
                            List(width * height) { pixels[it * 3 + band] }
                        }
                    }

                val output =
                    encode(
                        arena,
                        source,
                        transformation(channels, PixelLayout.PLANAR).copy(
                            width = width.toDimension(),
                            height = height.toDimension(),
                        ),
                    )

                output.toList() shouldBe expected
            }
        }

    private fun transformation(
        channels: String,
        layout: PixelLayout = PixelLayout.INTERLEAVED,
    ) = Transformation(
        width = 2.toDimension(),
        height = 1.toDimension(),
        format = ImageFormat.PIXELS,
        colorSpace = ColorSpace.SRGB,
        pixels = PixelTransformation(channels.toPixelChannels(), layout),
    )

    private suspend fun encode(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): ByteArray =
        coroutineScope {
            val output = ByteChannel(autoFlush = true)
            val bytes = async(Dispatchers.IO) { output.readRemaining().readByteArray() }
            try {
                RawPixelEncoder.writeToStream(arena, source, transformation, output)
                output.close()
                bytes.await()
            } finally {
                output.cancel(null)
            }
        }
}
