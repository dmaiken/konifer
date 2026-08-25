package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import io.konifer.PHash
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_EXTEND
import io.konifer.matchers.shouldHaveSamePixelContentAs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
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
        fun `can pad an image with color that is single channel grayscale`() {
            val padding = 40
            val background = listOf(200, 45, 55)
            val imageBytes = javaClass.getResourceAsStream("/images/colorspace/gray.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedBytes =
                javaClass
                    .getResourceAsStream(
                        "/images/expected/transform_pad_40_bg_200_45_45_0_gray.jpeg",
                    )!!
                    .readAllBytes()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")

                // Assert only one channel
                transformed.processed.getInt(OPTION_BANDS) shouldBe 3

                PHash.hammingDistance(expectedBytes, actualStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_IDENTICAL
            }
        }

        @Test
        fun `padding is not colorized if greyscale colorspace is specified`() {
            val padding = 40
            val background = listOf(200, 45, 55)
            val imageBytes = javaClass.getResourceAsStream("/images/colorspace/gray.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedBytes =
                javaClass
                    .getResourceAsStream(
                        "/images/expected/transform_pad_40_bg_200_45_45_0_gray.jpeg",
                    )!!
                    .readAllBytes()
            Vips.run { arena ->
                val transformed =
                    Pad.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, imageBytes),
                        transformation = padTransformation(padding, background, colorSpace = ColorSpace.Grayscale),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")

                // Assert only one channel
                transformed.processed.getInt(OPTION_BANDS) shouldBe 1

                PHash.hammingDistance(expectedBytes, actualStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_IDENTICAL
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
    inner class DecisionTests {
        @Test
        fun `does not require transformation if if pad is 0`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.decide(
                    TransformationContext(
                        arena,
                        VImage.newFromBytes(arena, imageBytes),
                        padTransformation(0, listOf(200, 45, 0)),
                        emptyList(),
                    ),
                ) shouldBe TransformationDecision.Skip
            }
        }

        @Test
        fun `does not require transformation if if background is empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.decide(
                    TransformationContext(
                        arena,
                        VImage.newFromBytes(arena, imageBytes),
                        padTransformation(10, emptyList()),
                        emptyList(),
                    ),
                ) shouldBe TransformationDecision.Skip
            }
        }

        @Test
        fun `requires transformation if if pad is greater than 0 and background is not empty`() {
            val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
            Vips.run { arena ->
                Pad.decide(
                    TransformationContext(
                        arena,
                        VImage.newFromBytes(arena, imageBytes),
                        padTransformation(1, listOf(200, 45, 0)),
                        emptyList(),
                    ),
                ) shouldBe
                    TransformationDecision.Apply(
                        requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                        requiredPixelAccess = PixelAccess.SEQUENTIAL,
                    )
            }
        }
    }

    private fun padTransformation(
        pad: Int,
        color: List<Int>,
        format: ImageFormat = ImageFormat.PNG,
        colorSpace: ColorSpace? = null,
    ) = Transformation(
        height = 10.toDimension(),
        width = 10.toDimension(),
        format = format,
        padding =
            PaddingTransformation(
                amount = pad.toPaddingAmount(),
                color = color,
            ),
        colorSpace = colorSpace ?: ColorSpace.SRGB,
        isColorSpaceLocked = colorSpace != null,
    )
}
