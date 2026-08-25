package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ForceRgbBandsTest {
    @Nested
    inner class TransformTests {
        @Test
        fun `converts grayscale image to three RGB bands`() {
            Vips.run { arena ->
                val transformed =
                    ForceRgbBands.transform(
                        arena = arena,
                        source = VImage.black(arena, 2, 2),
                        transformation = transformation,
                    )

                transformed.processed.getInt(OPTION_BANDS) shouldBe 3
                transformed.processed.hasAlpha() shouldBe false
                transformed.processed.getpoint(0, 0) shouldBe listOf(0.0, 0.0, 0.0)
                transformed.requiresLqipRegeneration shouldBe false
            }
        }

        @Test
        fun `flattens alpha over white background`() {
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, alphaPng())

                val transformed =
                    ForceRgbBands.transform(
                        arena = arena,
                        source = source,
                        transformation = transformation,
                    )

                val pixel = transformed.processed.getpoint(0, 0)

                transformed.processed.getInt(OPTION_BANDS) shouldBe 3
                transformed.processed.hasAlpha() shouldBe false
                pixel[0] shouldBe (127.0 plusOrMinus 1.0)
                pixel[1] shouldBe (177.0 plusOrMinus 1.0)
                pixel[2] shouldBe (152.0 plusOrMinus 1.0)
            }
        }
    }

    @Nested
    inner class DecisionTests {
        @Test
        fun `requires transformation when image has alpha`() {
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, alphaPng())

                ForceRgbBands.decide(
                    TransformationContext(arena, source, transformation, emptyList()),
                ) shouldBe
                    TransformationDecision.Apply(
                        requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                        requiredPixelAccess = PixelAccess.SEQUENTIAL,
                    )
            }
        }

        @Test
        fun `requires transformation when image is not three bands`() {
            Vips.run { arena ->
                ForceRgbBands.decide(
                    TransformationContext(arena, VImage.black(arena, 2, 2), transformation, emptyList()),
                ) shouldBe
                    TransformationDecision.Apply(
                        requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                        requiredPixelAccess = PixelAccess.SEQUENTIAL,
                    )
            }
        }

        @Test
        fun `does not require transformation after forcing RGB bands`() {
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, alphaPng())

                val transformed =
                    ForceRgbBands.transform(
                        arena = arena,
                        source = source,
                        transformation = transformation,
                    )

                ForceRgbBands.decide(
                    TransformationContext(arena, transformed.processed, transformation, emptyList()),
                ) shouldBe TransformationDecision.Skip
            }
        }
    }

    private companion object {
        val transformation =
            Transformation(
                width = 2.toDimension(),
                height = 2.toDimension(),
                format = ImageFormat.PNG,
                colorSpace = ColorSpace.SRGB,
            )

        fun alphaPng(): ByteArray {
            val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            val color = Color(0, 100, 50, 128).rgb
            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    image.setRGB(x, y, color)
                }
            }

            return ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)
                output.toByteArray()
            }
        }
    }
}
