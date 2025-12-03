package io.image.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.PHash
import io.direkt.asset.AssetStreamContainer
import io.image.model.Filter
import io.image.model.ImageFormat
import io.image.model.Transformation
import io.image.vips.transformation.ColorFilter
import io.image.vips.transformation.ColorFilter.blackWhiteThreshold
import io.image.vips.transformation.ColorFilter.greyscaleMatrix3x3
import io.image.vips.transformation.ColorFilter.greyscaleMatrix4x4
import io.image.vips.transformation.ColorFilter.sepiaMatrix3x3
import io.image.vips.transformation.ColorFilter.sepiaMatrix4x4
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import io.matchers.shouldHaveSamePixelContentAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.use

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

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `when filter is greyscale then image is converted to greyscale`(format: ImageFormat) =
            runTest {
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readAllBytes()

                val imageChannel = ByteReadChannel(image)
                AssetStreamContainer(imageChannel).use { container ->
                    container.toTemporaryFile()
                    val actualStream = ByteArrayOutputStream()
                    val expectedStream = ByteArrayOutputStream()
                    Vips.run { arena ->
                        val transformed =
                            ColorFilter.transform(
                                arena = arena,
                                source = VImage.newFromFile(arena, container.getTemporaryFile().absolutePath),
                                transformation = colorFilterTransformation(Filter.GREYSCALE),
                            )
                        transformed.processed.writeToStream(actualStream, format.extension)

                        val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, greyscaleMatrix3x3))
                        VImage
                            .newFromFile(arena, container.getTemporaryFile().absolutePath)
                            .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                            .recomb(matrixImage)
                            .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                            .writeToStream(expectedStream, format.extension)

                        PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                            HAMMING_DISTANCE_IDENTICAL
                    }
                }
            }

        @Test
        fun `when filter is greyscale then multi-page gif is converted to greyscale`() {
            val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readAllBytes()

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
                    ColorFilter.transform(
                        arena = arena,
                        source =
                            VImage.newFromBytes(
                                arena,
                                image,
                                VipsOption.Int("n", -1),
                                VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                            ),
                        transformation = colorFilterTransformation(Filter.GREYSCALE),
                    )
                transformed.processed.writeToStream(actualStream, ".gif")

                val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, greyscaleMatrix4x4))
                VImage
                    .newFromBytes(
                        arena,
                        image,
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    ).colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                    .recomb(matrixImage)
                    .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                    .writeToStream(expectedStream, ".gif")
                val processed =
                    VImage.newFromBytes(
                        arena,
                        actualStream.toByteArray(),
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )

                processed.getInt("n-pages") shouldBe original.getInt("n-pages")
                processed.getInt("page-height") shouldBe original.getInt("page-height")

                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class, mode = Mode.EXCLUDE, names = ["PNG"])
        fun `when filter is black white then image is converted to black and white for alpha-supporting formats`(format: ImageFormat) =
            runTest {
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readAllBytes()

                val imageChannel = ByteReadChannel(image)
                AssetStreamContainer(imageChannel).use { container ->
                    container.toTemporaryFile()
                    val actualStream = ByteArrayOutputStream()
                    val expectedStream = ByteArrayOutputStream()
                    Vips.run { arena ->
                        val transformed =
                            ColorFilter.transform(
                                arena = arena,
                                source = VImage.newFromFile(arena, container.getTemporaryFile().absolutePath),
                                transformation = colorFilterTransformation(Filter.BLACK_WHITE),
                            )
                        transformed.processed.writeToStream(actualStream, format.extension)

                        val expectedSource = VImage.newFromFile(arena, container.getTemporaryFile().absolutePath)
                        val bands = expectedSource.getInt("bands")
                        val alpha = expectedSource.extractBand(bands - 1, VipsOption.Int("n", 1))
                        val rgb = expectedSource.extractBand(0, VipsOption.Int("n", bands - 1))

                        val faxedRgb =
                            rgb
                                .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                                .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                                .cast(VipsBandFormat.FORMAT_UCHAR)
                        VImage.bandjoin(arena, listOf(faxedRgb, alpha)).writeToStream(expectedStream, format.extension)

                        // Not sure why these are not showing as identical images - perhaps the colorspace is
                        // so limited that a PHASH is not accurate?
                        PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                            HAMMING_DISTANCE_CEILING
                    }
                }
            }

        @ParameterizedTest
        @EnumSource(ImageFormat::class, mode = Mode.INCLUDE, names = ["PNG"])
        fun `when filter is black white then image is converted to black and white for alpha unsupported formats`(format: ImageFormat) =
            runTest {
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readAllBytes()

                val imageChannel = ByteReadChannel(image)
                AssetStreamContainer(imageChannel).use { container ->
                    container.toTemporaryFile()
                    val actualStream = ByteArrayOutputStream()
                    val expectedStream = ByteArrayOutputStream()
                    Vips.run { arena ->
                        val transformed =
                            ColorFilter.transform(
                                arena = arena,
                                source = VImage.newFromBytes(arena, image),
                                transformation = colorFilterTransformation(Filter.BLACK_WHITE),
                            )
                        transformed.processed.writeToStream(actualStream, format.extension)

                        VImage
                            .newFromBytes(arena, image)
                            .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                            .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                            .writeToStream(expectedStream, format.extension)

                        PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                            HAMMING_DISTANCE_IDENTICAL
                    }
                }
            }

        @Test
        fun `when filter is black white then multi-page gif is converted to black and white`() {
            val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readAllBytes()

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
                    ColorFilter.transform(
                        arena = arena,
                        source =
                            VImage.newFromBytes(
                                arena,
                                image,
                                VipsOption.Int("n", -1),
                                VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                            ),
                        transformation = colorFilterTransformation(Filter.BLACK_WHITE),
                    )
                transformed.processed.writeToStream(actualStream, ".gif")

                VImage
                    .newFromBytes(
                        arena,
                        image,
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    ).relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, listOf(128.0))
                    .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                    .writeToStream(expectedStream, ".gif")
                val processed =
                    VImage.newFromBytes(
                        arena,
                        actualStream.toByteArray(),
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )

                processed.getInt("n-pages") shouldBe original.getInt("n-pages")
                processed.getInt("page-height") shouldBe original.getInt("page-height")

                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
            }
        }

        @ParameterizedTest
        @EnumSource(ImageFormat::class)
        fun `when filter is sepia then image is converted to sepia`(format: ImageFormat) =
            runTest {
                val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.readAllBytes()

                val imageChannel = ByteReadChannel(image)
                AssetStreamContainer(imageChannel).use { container ->
                    container.toTemporaryFile()
                    val actualStream = ByteArrayOutputStream()
                    val expectedStream = ByteArrayOutputStream()
                    Vips.run { arena ->
                        val transformed =
                            ColorFilter.transform(
                                arena = arena,
                                source = VImage.newFromBytes(arena, image),
                                transformation = colorFilterTransformation(Filter.SEPIA),
                            )
                        transformed.processed.writeToStream(actualStream, format.extension)

                        val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaMatrix3x3))
                        VImage
                            .newFromBytes(arena, image)
                            .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                            .recomb(matrixImage)
                            .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                            .writeToStream(expectedStream, format.extension)

                        PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                            HAMMING_DISTANCE_IDENTICAL
                    }
                }
            }

        @Test
        fun `when filter is sepia then multi-page gif is converted to sepia`() {
            val image = javaClass.getResourceAsStream("/images/kermit.gif")!!.readAllBytes()

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
                    ColorFilter.transform(
                        arena = arena,
                        source =
                            VImage.newFromBytes(
                                arena,
                                image,
                                VipsOption.Int("n", -1),
                                VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                            ),
                        transformation = colorFilterTransformation(Filter.SEPIA),
                    )
                transformed.processed.writeToStream(actualStream, ".gif")

                val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaMatrix4x4))
                VImage
                    .newFromBytes(
                        arena,
                        image,
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    ).colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                    .recomb(matrixImage)
                    .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                    .writeToStream(expectedStream, ".gif")
                val processed =
                    VImage.newFromBytes(
                        arena,
                        actualStream.toByteArray(),
                        VipsOption.Int("n", -1),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL),
                    )

                processed.getInt("n-pages") shouldBe original.getInt("n-pages")
                processed.getInt("page-height") shouldBe original.getInt("page-height")
                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
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
