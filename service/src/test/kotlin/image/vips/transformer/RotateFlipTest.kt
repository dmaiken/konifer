package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsDirection
import io.image.model.Rotate
import io.image.vips.transformation.RotateFlip
import io.kotest.matchers.shouldBe
import io.matcher.shouldHaveSamePixelContentAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class RotateFlipTest {

    companion object {
        @JvmStatic
        fun rotateFlipSource() = listOf(
            arguments(Rotate.ZERO, false),
            arguments(Rotate.ZERO, true),
            arguments(Rotate.NINETY, false),
            arguments(Rotate.NINETY, true),
            arguments(Rotate.ONE_HUNDRED_EIGHTY, false),
            arguments(Rotate.ONE_HUNDRED_EIGHTY, true),
            arguments(Rotate.TWO_HUNDRED_SEVENTY, false),
            arguments(Rotate.TWO_HUNDRED_SEVENTY, true),
        )
    }

    @ParameterizedTest
    @MethodSource("rotateFlipSource")
    fun `can rotate and flip`(rotate: Rotate, horizontalFlip: Boolean) = runTest {
        val rotateFlip = RotateFlip(
            rotate = rotate,
            horizontalFlip = horizontalFlip
        )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        Vips.run { arena ->
            val transformed = rotateFlip.transform(VImage.newFromBytes(arena, image))

            val angle = when (rotate) {
                Rotate.ZERO -> 0
                Rotate.NINETY -> 90
                Rotate.ONE_HUNDRED_EIGHTY -> 180
                Rotate.TWO_HUNDRED_SEVENTY -> 270
                else -> throw IllegalStateException()
            }.toDouble()
            val expected = VImage.newFromBytes(arena, image)
                .rotate(angle)
                .let {
                    if (horizontalFlip) {
                        it.flip(VipsDirection.DIRECTION_HORIZONTAL)
                    } else {
                        it
                    }
                }

            val expectedStream = ByteArrayOutputStream()
            expected.writeToStream(expectedStream, ".jpeg")
            val actualStream = ByteArrayOutputStream()
            transformed.writeToStream(actualStream, ".jpeg")

            val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            actualImage shouldHaveSamePixelContentAs expectedImage
            transformed.getInt("orientation") shouldBe 1
        }
    }
}