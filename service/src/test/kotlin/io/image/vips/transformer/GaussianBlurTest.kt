package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.image.model.ImageFormat
import io.image.model.Transformation
import io.image.vips.transformation.GaussianBlur
import io.kotest.matchers.shouldBe
import io.matchers.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GaussianBlurTest {
    @Nested
    inner class TransformTests {
        @ParameterizedTest
        @ValueSource(ints = [1, 2, 10, 30, 150])
        fun `can apply a gaussian blur to an image`(amount: Int) {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    GaussianBlur.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = blurTransformation(amount),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                VImage
                    .newFromBytes(arena, image)
                    .gaussblur(amount / 2.0)
                    .writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }

        @Test
        fun `if blur is 0 then image is not blurred`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    GaussianBlur.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = blurTransformation(0),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                VImage
                    .newFromBytes(arena, image)
                    .writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 150])
        fun `lqip regeneration is not required`(amount: Int) {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                GaussianBlur
                    .transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = blurTransformation(amount),
                    ).requiresLqipRegeneration shouldBe false
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `does not require transformation if blur is 0`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                GaussianBlur.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation = blurTransformation(0),
                ) shouldBe false
            }
        }

        @Test
        fun `requires transformation if blur is greater than 0`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                GaussianBlur.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation = blurTransformation(1),
                ) shouldBe true
            }
        }
    }

    private fun blurTransformation(blur: Int) =
        Transformation(
            height = 10,
            width = 10,
            format = ImageFormat.PNG,
            blur = blur,
        )
}
