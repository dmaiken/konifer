package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.image.vips.transformation.GaussianBlur
import io.kotest.matchers.shouldBe
import io.matcher.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GaussianBlurTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 10, 30, 150])
    fun `can apply a gaussian blur to an image`(amount: Int) {
        val gaussianBlur =
            GaussianBlur(
                blurAmount = amount,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = gaussianBlur.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            VImage.newFromBytes(arena, image)
                .gaussblur(amount / 2.0)
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }

    @Test
    fun `if blur is 0 then image is not blurred`() {
        val gaussianBlur =
            GaussianBlur(
                blurAmount = 0,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = gaussianBlur.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            VImage.newFromBytes(arena, image)
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 150])
    fun `lqip regeneration is not required`(amount: Int) {
        val gaussianBlur =
            GaussianBlur(
                blurAmount = amount,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        Vips.run { arena ->
            gaussianBlur.requiresLqipRegeneration(VImage.newFromBytes(arena, image)) shouldBe false
        }
    }
}
