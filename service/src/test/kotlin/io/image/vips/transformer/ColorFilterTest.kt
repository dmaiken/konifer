package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.image.model.Filter
import io.image.model.ImageFormat
import io.image.model.Transformation
import io.image.vips.transformation.ColorFilter
import io.image.vips.transformation.ColorFilter.sepiaMatrix
import io.kotest.matchers.shouldBe
import io.matchers.shouldHaveSamePixelContentAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ColorFilterTest {
    @Nested
    inner class TransformTests {
        @Test
        fun `when filter is none then source is returned`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    ColorFilter.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = colorFilterTransformation(Filter.NONE),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                VImage.newFromBytes(arena, image).writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }

        @Test
        fun `when filter is greyscale then image is converted to greyscale`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    ColorFilter.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = colorFilterTransformation(Filter.GREYSCALE),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                VImage.newFromBytes(arena, image)
                    .colourspace(VipsInterpretation.INTERPRETATION_GREY16)
                    .writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }

        @Test
        fun `when filter is black white then image is converted to black and white`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    ColorFilter.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = colorFilterTransformation(Filter.BLACK_WHITE),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                VImage.newFromBytes(arena, image)
                    .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, listOf(128.0))
                    .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                    .writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }

        @Test
        fun `when filter is sepia then image is converted to sepia`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    ColorFilter.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = colorFilterTransformation(Filter.SEPIA),
                    )
                transformed.processed.writeToStream(actualStream, ".jpeg")
                val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

                val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaMatrix))
                VImage.newFromBytes(arena, image)
                    .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                    .recomb(matrixImage)
                    .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                    .writeToStream(expectedStream, ".jpeg")

                actualImage shouldHaveSamePixelContentAs ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `does not require transformation if filter is NONE`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                ColorFilter.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation = colorFilterTransformation(Filter.NONE),
                ) shouldBe false
            }
        }

        @ParameterizedTest
        @EnumSource(Filter::class, mode = Mode.EXCLUDE, names = ["NONE"])
        fun `requires transformation if filter is not NONE`(filter: Filter) {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                ColorFilter.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation = colorFilterTransformation(filter),
                ) shouldBe true
            }
        }
    }

    private fun colorFilterTransformation(filter: Filter) =
        Transformation(
            height = 10,
            width = 10,
            format = ImageFormat.PNG,
            filter = filter,
        )
}
