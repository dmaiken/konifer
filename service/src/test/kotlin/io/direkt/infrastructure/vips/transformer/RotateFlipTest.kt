package io.direkt.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsDirection
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.image.Transformation
import io.direkt.infrastructure.vips.transformation.RotateFlip
import io.kotest.matchers.shouldBe
import io.matchers.shouldHaveSamePixelContentAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class RotateFlipTest {
    companion object {
        @JvmStatic
        fun rotateFlipSource() =
            listOf(
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
    fun `can rotate and flip`(
        rotate: Rotate,
        horizontalFlip: Boolean,
    ) = runTest {
        val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

        Vips.run { arena ->
            val transformed =
                RotateFlip.transform(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation =
                        Transformation(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                            rotate = rotate,
                            horizontalFlip = horizontalFlip,
                        ),
                )

            val angle =
                when (rotate) {
                    Rotate.ZERO -> 0
                    Rotate.NINETY -> 90
                    Rotate.ONE_HUNDRED_EIGHTY -> 180
                    Rotate.TWO_HUNDRED_SEVENTY -> 270
                    else -> throw IllegalStateException()
                }.toDouble()
            val expected =
                VImage
                    .newFromBytes(arena, image)
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
            transformed.processed.writeToStream(actualStream, ".jpeg")

            val expectedImage = ImageIO.read(ByteArrayInputStream(expectedStream.toByteArray()))
            val actualImage = ImageIO.read(ByteArrayInputStream(actualStream.toByteArray()))

            actualImage shouldHaveSamePixelContentAs expectedImage
            transformed.processed.getInt("orientation") shouldBe 1
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `does not require transformation if rotate is zero and no horizontal flip`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                RotateFlip.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation =
                        Transformation(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                            rotate = Rotate.ZERO,
                            horizontalFlip = false,
                        ),
                ) shouldBe false
            }
        }

        @ParameterizedTest
        @EnumSource(Rotate::class, mode = EnumSource.Mode.EXCLUDE, names = ["ZERO"])
        fun `requires transformation if rotate is not zero and no horizontal flip`(rotate: Rotate) {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                RotateFlip.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation =
                        Transformation(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                            rotate = rotate,
                            horizontalFlip = false,
                        ),
                ) shouldBe true
            }
        }

        @Test
        fun `requires transformation if horizontal flip`() {
            val image = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()

            Vips.run { arena ->
                RotateFlip.requiresTransformation(
                    arena = arena,
                    source = VImage.newFromBytes(arena, image),
                    transformation =
                        Transformation(
                            width = 10,
                            height = 10,
                            format = ImageFormat.PNG,
                            rotate = Rotate.ZERO,
                            horizontalFlip = true,
                        ),
                ) shouldBe true
            }
        }
    }
}
