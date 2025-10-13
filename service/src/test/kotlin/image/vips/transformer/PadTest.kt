package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsExtend
import image.model.ImageFormat
import io.PHash
import io.image.vips.VipsOption.VIPS_BACKGROUND
import io.image.vips.VipsOption.VIPS_EXTEND
import io.image.vips.transformation.Pad
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.matcher.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PadTest {
    @Test
    fun `can pad an image with a background containing no alpha`() {
        val padding = 40
        val background = listOf(200, 45, 55)
        val pad =
            Pad(
                pad = padding,
                background = background,
                format = ImageFormat.PNG,
            )

        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val image = VImage.newFromBytes(arena, imageBytes)
            image.embed(
                padding,
                padding,
                (padding * 2) + image.width,
                (padding * 2) + image.height,
                VipsOption.Enum(VIPS_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                VipsOption.ArrayDouble(VIPS_BACKGROUND, background.map { it.toDouble() }),
            )
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            actualImage.width shouldBe image.width + (padding * 2)
            actualImage.height shouldBe image.height + (padding * 2)
        }
    }

    @Test
    fun `can pad an image with alpha band with a background containing alpha`() {
        val padding = 40
        val background = listOf(255, 0, 0, 100)
        val pad =
            Pad(
                pad = padding,
                background = background,
                format = ImageFormat.PNG,
            )

        val imageBytes = javaClass.getResourceAsStream("/images/moon_transparency.png")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedBytes = javaClass.getResourceAsStream("/images/expected/transform_pad_${padding}_bg_255_0_0_100.png")!!.readAllBytes()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            transformed.writeToStream(actualStream, ".png")

            PHash.hammingDistance(expectedBytes, actualStream.toByteArray()) shouldBeLessThan HAMMING_DISTANCE_IDENTICAL
        }
    }

    @Test
    fun `can pad an image without alpha band with a background containing alpha`() {
        val padding = 75
        val background = listOf(25, 20, 160, 50)
        val pad =
            Pad(
                pad = padding,
                background = background,
                format = ImageFormat.PNG,
            )

        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".png")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val expectedImage = VImage.newFromBytes(arena, image)
            expectedImage
                .bandjoinConst(listOf(255.0))
                .embed(
                    padding,
                    padding,
                    (padding * 2) + expectedImage.width,
                    (padding * 2) + expectedImage.height,
                    VipsOption.Enum(VIPS_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                    VipsOption.ArrayDouble(VIPS_BACKGROUND, background.map { it.toDouble() }),
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
        val pad =
            Pad(
                pad = padding,
                background = background,
                format = ImageFormat.JPEG,
            )

        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val image = VImage.newFromBytes(arena, imageBytes)
            image.embed(
                padding,
                padding,
                (padding * 2) + image.width,
                (padding * 2) + image.height,
                VipsOption.Enum(VIPS_EXTEND, VipsExtend.EXTEND_BACKGROUND),
                VipsOption.ArrayDouble(VIPS_BACKGROUND, background.take(3).map { it.toDouble() }),
            )
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            actualImage.width shouldBe image.width + (padding * 2)
            actualImage.height shouldBe image.height + (padding * 2)
        }
    }

    @Test
    fun `if pad is zero then nothing is padded`() {
        val background = listOf(200, 45, 55, 2)
        val pad =
            Pad(
                pad = 0,
                background = background,
                format = ImageFormat.JPEG,
            )

        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val image = VImage.newFromBytes(arena, imageBytes)
            image.writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            actualImage.width shouldBe image.width
            actualImage.height shouldBe image.height
        }
    }

    @Test
    fun `if background is empty then nothing is padded`() {
        val pad =
            Pad(
                pad = 40,
                background = emptyList(),
                format = ImageFormat.JPEG,
            )

        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val image = VImage.newFromBytes(arena, imageBytes)
            image.writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            actualImage.width shouldBe image.width
            actualImage.height shouldBe image.height
        }
    }

    @Test
    fun `throws if background size is greater than 4`() {
        val pad =
            Pad(
                pad = 40,
                background = listOf(200, 45, 55, 2, 5),
                format = ImageFormat.JPEG,
            )
        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        shouldThrow<IllegalArgumentException> {
            Vips.run { arena ->
                pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            }
        }
    }

    @Test
    fun `throws if background size is less than 3`() {
        val pad =
            Pad(
                pad = 40,
                background = listOf(200, 45),
                format = ImageFormat.JPEG,
            )
        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        shouldThrow<IllegalArgumentException> {
            Vips.run { arena ->
                pad.transform(arena, VImage.newFromBytes(arena, imageBytes))
            }
        }
    }

    @Test
    fun `lqips are not required to be regenerated if padding is greater than 0`() {
        val pad =
            Pad(
                pad = 0,
                background = listOf(200, 45, 0),
                format = ImageFormat.JPEG,
            )
        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
        Vips.run { arena ->
            pad.requiresLqipRegeneration(VImage.newFromBytes(arena, imageBytes)) shouldBe false
        }
    }

    @Test
    fun `lqips are required to be regenerated if background is empty`() {
        val pad =
            Pad(
                pad = 10,
                background = emptyList(),
                format = ImageFormat.JPEG,
            )
        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
        Vips.run { arena ->
            pad.requiresLqipRegeneration(VImage.newFromBytes(arena, imageBytes)) shouldBe false
        }
    }

    @Test
    fun `lqips are required to be regenerated if background is not empty`() {
        val pad =
            Pad(
                pad = 10,
                background = listOf(200, 45, 0),
                format = ImageFormat.JPEG,
            )
        val imageBytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
        Vips.run { arena ->
            pad.requiresLqipRegeneration(VImage.newFromBytes(arena, imageBytes)) shouldBe true
        }
    }
}
