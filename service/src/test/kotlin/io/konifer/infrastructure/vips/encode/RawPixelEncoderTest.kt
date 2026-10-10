package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsBandFormat
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
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
        fun floatPixelLayouts() =
            listOf(
                arguments("RGB", listOf(0, 2, 255, 255, 128, 0)),
                arguments("BGRA", listOf(255, 2, 0, 255, 0, 128, 255, 255)),
            )
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
    @MethodSource("floatPixelLayouts")
    fun `casts samples to unsigned bytes before writing`(
        layout: String,
        expected: List<Int>,
    ) = runTest {
        Arena.ofConfined().use { arena ->
            val source =
                VImage.newFromMemory(
                    arena,
                    arena.allocateFrom(ValueLayout.JAVA_FLOAT, -1f, 2.9f, 300f, 255.9f, 128.75f, 0f),
                    2,
                    1,
                    3,
                    VipsBandFormat.FORMAT_FLOAT.rawValue,
                )

            encode(arena, source, transformation(layout)).toList() shouldBe expected.map(Int::toByte)
        }
    }

    @Test
    fun `writes multiple rows without headers or padding`() =
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

                encode(arena, source, transformation("RGB").copy(height = 2.toDimension())).toList() shouldBe pixels.toList()
            }
        }

    @Test
    fun `rejects transformation without pixel layout`() {
        Vips.run { arena ->
            val source = VImage.black(arena, 2, 1).bandjoinConst(listOf(0.0, 0.0))

            shouldThrow<IllegalStateException> {
                RawPixelEncoder.writeToStream(arena, source, transformation("RGB").copy(pixels = null), ByteChannel())
            }.message shouldBe "Pixel layout missing despite format of pixels"
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

    private fun transformation(layout: String) =
        Transformation(
            width = 2.toDimension(),
            height = 1.toDimension(),
            format = ImageFormat.PIXELS,
            colorSpace = ColorSpace.SRGB,
            pixels = PixelTransformation(layout.toPixelChannels()),
        )

    private suspend fun encode(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): ByteArray {
        val output = ByteChannel(autoFlush = true)
        try {
            RawPixelEncoder.writeToStream(arena, source, transformation, output)
            output.close()
            return output.readRemaining().readByteArray()
        } finally {
            output.cancel(null)
        }
    }
}
