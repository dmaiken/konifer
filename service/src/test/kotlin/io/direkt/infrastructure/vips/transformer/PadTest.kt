package io.direkt.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import io.PHash
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Transformation
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_EXTEND
import io.direkt.infrastructure.vips.transformation.Pad
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.matchers.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PadTest {
    @Nested
    inner class TransformTests {
        @Test
        fun `can pad an image with a background containing no alpha`() {
            val padding = 40
            val background = listOf(200, 45, 55)
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                val image = VImage.newFromBytes(arena, imageBytes)
                image
                    .embed(
                        padding,
                        padding,
                        (padding * 2) + image.width,
                        (padding * 2) + image.height,
                        VipsOption.Enum(OPTION_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                        VipsOption.ArrayDouble(OPTION_BACKGROUND, background.map { it.toDouble() }),
                    ).writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                actualImage.width shouldBe image.width + (padding * 2)
                actualImage.height shouldBe image.height + (padding * 2)
            }
        }

        @Test
        fun `can pad an image with alpha band with a background containing alpha`() {
            val padding = 40
            val background = listOf(255, 0, 0, 100)
            val imageBytes = javaClass.getResourceAsStream("/images/moon_transparency.png")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedBytes =
                javaClass
                    .getResourceAsStream(
                        "/images/expected/transform_pad_${padding}_bg_255_0_0_100.png",
                    )!!
                    .readAllBytes()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background),
                    )
                transformed.processed.writeToStream(actualStream, ".png")

                PHash.hammingDistance(expectedBytes, actualStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_IDENTICAL
            }
        }

        @Test
        fun `can pad an image without alpha band with a background containing alpha`() {
            val padding = 75
            val background = listOf(25, 20, 160, 50)
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background),
                    )
                transformed.processed.writeToStream(actualStream, ".png")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                val expectedImage = VImage.newFromBytes(arena, imageBytes)
                expectedImage
                    .bandjoinConst(listOf(255.0))
                    .embed(
                        padding,
                        padding,
                        (padding * 2) + expectedImage.width,
                        (padding * 2) + expectedImage.height,
                        VipsOption.Enum(OPTION_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                        VipsOption.ArrayDouble(OPTION_BACKGROUND, background.map { it.toDouble() }),
                    ).writeToStream(expectedStream, ".png")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                actualImage.width shouldBe expectedImage.width + (padding * 2)
                actualImage.height shouldBe expectedImage.height + (padding * 2)
            }
        }

        @Test
        fun `if transforming to jpeg then alpha channel is ignored`() {
            val padding = 40
            val background = listOf(200, 45, 55, 2)
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background, ImageFormat.JPEG),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                val image = VImage.newFromBytes(arena, imageBytes)
                image
                    .embed(
                        padding,
                        padding,
                        (padding * 2) + image.width,
                        (padding * 2) + image.height,
                        VipsOption.Enum(OPTION_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                        VipsOption.ArrayDouble(OPTION_BACKGROUND, background.take(3).map { it.toDouble() }),
                    ).writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                actualImage.width shouldBe image.width + (padding * 2)
                actualImage.height shouldBe image.height + (padding * 2)
            }
        }

        @Test
        fun `if pad is zero then nothing is padded`() {
            val background = listOf(200, 45, 55, 2)
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(0, background),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                val image = VImage.newFromBytes(arena, imageBytes)
                image.writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
                actualImage.width shouldBe image.width
                actualImage.height shouldBe image.height
            }
        }

        @Test
        fun `throws if background is empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            shouldThrow<IllegalArgumentException> {
                Vips.run { arena ->
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(40, emptyList()),
                    )
                }
            }
        }

        @Test
        fun `throws if background size is greater than 4`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            shouldThrow<IllegalArgumentException> {
                Vips.run { arena ->
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(40, listOf(200, 45, 55, 2, 5)),
                    )
                }
            }
        }

        @Test
        fun `throws if background size is less than 3`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            shouldThrow<IllegalArgumentException> {
                Vips.run { arena ->
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(40, listOf(200, 45)),
                    )
                }
            }
        }

        @Test
        fun `lqips are required to be regenerated if padding is greater than 0`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad
                    .transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(10, listOf(200, 45, 0)),
                    ).requiresLqipRegeneration shouldBe true
            }
        }

        @Test
        fun `lqips are required to be regenerated if background is not empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad
                    .transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(10, listOf(200, 45, 0)),
                    ).requiresLqipRegeneration shouldBe true
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `does not require transformation if if pad is 0`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, imageBytes),
                    transformation = padTransformation(0, listOf(200, 45, 0)),
                ) shouldBe false
            }
        }

        @Test
        fun `does not require transformation if if background is empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, imageBytes),
                    transformation = padTransformation(10, emptyList()),
                ) shouldBe false
            }
        }

        @Test
        fun `requires transformation if if pad is greater than 0 and background is not empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, imageBytes),
                    transformation = padTransformation(1, listOf(200, 45, 0)),
                ) shouldBe true
            }
        }
    }

    private fun padTransformation(
        pad: Int,
        background: List<Int>,
        format: ImageFormat = ImageFormat.PNG,
    ) = Transformation(
        height = 10,
        width = 10,
        format = format,
        pad = pad,
        background = background,
    )
}
