package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.MetadataTransformation
import io.konifer.domain.variant.PaddingTransformation
import io.konifer.domain.variant.Transformation
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.TreeSet

class VariantParameterGeneratorTest {
    @Test
    fun `can generate variant attributes`() {
        val expectedAttributes =
            Json.encodeToString(
                ImageVariantAttributes(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                    colorSpace = ColorSpace.P3,
                ),
            )
        val attributes =
            VariantParameterGenerator.generateImageVariantAttributes(
                imageAttributes =
                    Attributes(
                        width = 100,
                        height = 100,
                        format = ImageFormat.JPEG,
                        colorSpace = ColorSpace.P3,
                    ),
            )

        attributes shouldBe expectedAttributes
    }

    @Test
    fun `the same transformations generated based on the same parameters`() {
        val expectedTransformation =
            Json.encodeToString(
                ImageVariantTransformation(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                    fit = Fit.FILL,
                    gravity = Gravity.ENTROPY,
                    rotate = Rotate.ONE_HUNDRED_EIGHTY,
                    horizontalFlip = true,
                    filter = Filter.SEPIA,
                    blur = 10,
                    quality = 30,
                    padding =
                        ImageVariantPadding(
                            amount = 10,
                            color = listOf(100, 100, 50, 10),
                        ),
                    metadata =
                        ImageVariantMetadata(
                            strip = listOf(MetadataType.IPTC, MetadataType.XMP),
                        ),
                    colorSpace = ColorSpace.P3,
                ),
            )
        val transformations1 =
            VariantParameterGenerator.generateImageVariantTransformations(
                imageTransformation =
                    Transformation(
                        height = 100,
                        width = 100,
                        format = ImageFormat.JPEG,
                        fit = Fit.FILL,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.SEPIA,
                        blur = 10,
                        quality = 30,
                        padding =
                            PaddingTransformation(
                                amount = 10,
                                color = listOf(100, 100, 50, 10),
                            ),
                        metadata =
                            MetadataTransformation(
                                strip = setOf(MetadataType.XMP, MetadataType.IPTC),
                            ),
                        colorSpace = ColorSpace.P3,
                    ),
            )
        val transformations2 =
            VariantParameterGenerator.generateImageVariantTransformations(
                imageTransformation =
                    Transformation(
                        height = 100,
                        width = 100,
                        format = ImageFormat.JPEG,
                        fit = Fit.FILL,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.SEPIA,
                        blur = 10,
                        quality = 30,
                        padding =
                            PaddingTransformation(
                                amount = 10,
                                color = listOf(100, 100, 50, 10),
                            ),
                        metadata =
                            MetadataTransformation(
                                strip = setOf(MetadataType.IPTC, MetadataType.XMP),
                            ),
                        colorSpace = ColorSpace.P3,
                    ),
            )

        transformations1 shouldBe transformations2 shouldBe expectedTransformation
    }

    @Test
    fun `default fields are ignored when serializing`() {
        val transformation =
            Transformation(
                height = 100,
                width = 150,
                format = ImageFormat.JPEG,
                colorSpace = ColorSpace.SRGB,
            )
        val expected =
            RequiredTransformationFields(
                height = 100,
                width = 150,
                format = ImageFormat.JPEG,
                colorSpace = ColorSpace.SRGB,
            )

        VariantParameterGenerator.generateImageVariantTransformations(transformation) shouldBe Json.encodeToString(expected)
    }

    @Test
    fun `metadata strip field is sorted alphabetically when serializing`() {
        val transformation =
            Transformation(
                height = 100,
                width = 150,
                format = ImageFormat.JPEG,
                metadata =
                    MetadataTransformation(
                        strip =
                            TreeSet<MetadataType>().apply {
                                add(MetadataType.XMP)
                                add(MetadataType.EXIF)
                                add(MetadataType.IPTC)
                            },
                    ),
                colorSpace = ColorSpace.SRGB,
            )
        val expected =
            ImageVariantTransformation(
                height = 100,
                width = 150,
                format = ImageFormat.JPEG,
                metadata =
                    ImageVariantMetadata(
                        strip = listOf(MetadataType.EXIF, MetadataType.IPTC, MetadataType.XMP),
                    ),
                colorSpace = ColorSpace.SRGB,
            )

        VariantParameterGenerator.generateImageVariantTransformations(transformation) shouldBe Json.encodeToString(expected)
    }

    @Serializable
    data class RequiredTransformationFields(
        val width: Int,
        val height: Int,
        val format: ImageFormat,
        @Serializable(with = ColorSpaceSerializer::class)
        val colorSpace: ColorSpace = ColorSpace.SRGB,
    )
}
