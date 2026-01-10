package io.direkt.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.direkt.PHash
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.vips.VipsOptionNames
import io.direkt.infrastructure.vips.createDecoderOptions
import io.direkt.infrastructure.vips.transformation.CropFirstPage
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream

class CropFirstPageTest {
    @Nested
    inner class TransformTests {
        @ParameterizedTest
        @MethodSource("io.direkt.domain.image.ImageTestSources#supportsPagedSource")
        fun `can crop first page of multi-page image`(format: ImageFormat) {
            val image =
                javaClass.getResourceAsStream("/images/kermit/kermit${format.extension}")!!.use {
                    it.readBytes()
                }

            val decoderOptions =
                createDecoderOptions(
                    sourceFormat = format,
                    destinationFormat = format,
                )
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image, *decoderOptions)
                val transformation =
                    Transformation(
                        height = source.height,
                        width = source.width,
                        format = format,
                    )
                val processed = CropFirstPage.transform(arena, source, transformation)

                val singlePageSource =
                    VImage.newFromBytes(
                        arena,
                        image,
                        VipsOption.Int(VipsOptionNames.OPTION_N, 1),
                        VipsOption.Enum(VipsOptionNames.OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
                    )

                val expectedStream = ByteArrayOutputStream()
                singlePageSource.writeToStream(expectedStream, format.extension)
                val actualStream = ByteArrayOutputStream()
                processed.processed.writeToStream(actualStream, format.extension)

                processed.processed.height shouldBe singlePageSource.height
                processed.processed.width shouldBe singlePageSource.width
                PHash.hammingDistance(actualStream.toByteArray(), expectedStream.toByteArray()) shouldBeLessThanOrEqual
                    HAMMING_DISTANCE_IDENTICAL
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @ParameterizedTest
        @MethodSource("io.direkt.domain.image.ImageTestSources#notSupportsPagedSource")
        fun `images that do not support multi-page are skipped`(format: ImageFormat) {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }

            val decoderOptions =
                createDecoderOptions(
                    sourceFormat = format,
                    destinationFormat = format,
                )
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image, *decoderOptions)
                val transformation =
                    Transformation(
                        height = source.height,
                        width = source.width,
                        format = format,
                    )
                CropFirstPage.requiresTransformation(arena, source, transformation) shouldBe false
            }
        }

        @ParameterizedTest
        @MethodSource("io.direkt.domain.image.ImageTestSources#supportsPagedSource")
        fun `images that do support multi-page are skipped if they are only one page`(format: ImageFormat) {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree${format.extension}")!!.use {
                    it.readBytes()
                }

            val decoderOptions =
                createDecoderOptions(
                    sourceFormat = format,
                    destinationFormat = format,
                )
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image, *decoderOptions)
                val transformation =
                    Transformation(
                        height = source.height,
                        width = source.width,
                        format = format,
                    )
                CropFirstPage.requiresTransformation(arena, source, transformation) shouldBe false
            }
        }

        @ParameterizedTest
        @MethodSource("io.direkt.domain.image.ImageTestSources#supportsPagedSource")
        fun `images that do support multi-page are not skipped if they have more than one page`(format: ImageFormat) {
            val image =
                javaClass.getResourceAsStream("/images/kermit/kermit${format.extension}")!!.use {
                    it.readBytes()
                }

            val decoderOptions =
                createDecoderOptions(
                    sourceFormat = format,
                    destinationFormat = format,
                )
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image, *decoderOptions)
                val transformation =
                    Transformation(
                        height = source.height,
                        width = source.width,
                        format = format,
                    )
                CropFirstPage.requiresTransformation(arena, source, transformation) shouldBe true
            }
        }
    }
}
