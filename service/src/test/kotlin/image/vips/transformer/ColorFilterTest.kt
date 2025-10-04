package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.image.model.Filter
import io.image.vips.transformation.ColorFilter
import io.image.vips.transformation.ColorFilter.Companion.blackWhiteThreshold
import io.image.vips.transformation.ColorFilter.Companion.sepiaText
import io.matcher.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ColorFilterTest {
    @Test
    fun `when filter is none then source is returned`() {
        val colorFilter =
            ColorFilter(
                filter = Filter.NONE,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = colorFilter.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            VImage.newFromBytes(arena, image).writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }

    @Test
    fun `when filter is greyscale then image is converted to greyscale`() {
        val colorFilter =
            ColorFilter(
                filter = Filter.GREYSCALE,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = colorFilter.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            VImage.newFromBytes(arena, image)
                .colourspace(VipsInterpretation.INTERPRETATION_GREY16)
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }

    @Test
    fun `when filter is black white then image is converted to black and white`() {
        val colorFilter =
            ColorFilter(
                filter = Filter.BLACK_WHITE,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = colorFilter.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            VImage.newFromBytes(arena, image)
                .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }

    @Test
    fun `when filter is sepia then image is converted to sepia`() {
        val colorFilter =
            ColorFilter(
                filter = Filter.SEPIA,
            )
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        val actualStream = ByteArrayOutputStream()
        val expectedStream = ByteArrayOutputStream()
        Vips.run { arena ->
            val transformed = colorFilter.transform(arena, VImage.newFromBytes(arena, image))
            transformed.writeToStream(actualStream, ".jpeg")
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaText))
            VImage.newFromBytes(arena, image)
                .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                .recomb(matrixImage)
                .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                .writeToStream(expectedStream, ".jpeg")

            actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
        }
    }
}
