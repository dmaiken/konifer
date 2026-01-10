package io.direkt.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.direkt.PHash
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.vips.transformation.GaussianBlur
import io.direkt.matchers.shouldHaveSamePixelContentAs
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
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

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `gaussian blur works on all formats`(format: ImageFormat) {
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val transformed =
                    GaussianBlur.transform(
                        arena = arena,
                        source = VImage.newFromBytes(arena, image),
                        transformation = blurTransformation(10),
                    )
                transformed.processed.writeToStream(actualStream, format.extension)

                VImage
                    .newFromBytes(arena, image)
                    .gaussblur(10 / 2.0)
                    .writeToStream(expectedStream, format.extension)

                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
            }
        }

        @ParameterizedTest
        @MethodSource("io.direkt.domain.image.ImageTestSources#supportsPagedSource")
        fun `gaussian blur works on multi-page image`(format: ImageFormat) {
            val image = javaClass.getResourceAsStream("/images/kermit/kermit${format.extension}")!!.readAllBytes()

            val actualStream = ByteArrayOutputStream()
            val expectedStream = ByteArrayOutputStream()
            Vips.run { arena ->
                val original =
                    VImage.newFromBytes(
                        arena,
                        image,
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )
                val transformed =
                    GaussianBlur.transform(
                        arena = arena,
                        source =
                            VImage.newFromBytes(
                                arena,
                                image,
                                VipsOption.Int("n", -1),
                                VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                            ),
                        transformation = blurTransformation(10),
                    )
                transformed.processed.writeToStream(actualStream, format.extension)

                VImage
                    .newFromBytes(
                        arena,
                        image,
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    ).gaussblur(10 / 2.0)
                    .writeToStream(expectedStream, format.extension)

                transformed.processed.getInt("n-pages") shouldBe original.getInt("n-pages")
                transformed.processed.getInt("page-height") shouldBe original.getInt("page-height")

                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
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
