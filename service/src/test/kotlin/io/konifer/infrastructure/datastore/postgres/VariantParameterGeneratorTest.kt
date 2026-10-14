package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.attribute.PixelAttributes
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
                    orientation = 6,
                    colorSpace = ColorSpace.P3,
                    pageCount = 10,
                    loop = 2,
                    pixels =
                        ImageVariantPixelAttributes(
                            channels = "RGBA".toPixelChannels(),
                            layout = PixelLayout.INTERLEAVED,
                            dataType = PixelDataType.UINT8,
                        ),
                ),
            )
        val attributes =
            VariantParameterGenerator.generateImageVariantAttributes(
                attributes =
                    Attributes(
                        width = 100.toDimension(),
                        height = 100.toDimension(),
                        format = ImageFormat.JPEG,
                        orientation = 6,
                        colorSpace = ColorSpace.P3,
                        pageCount = 10,
                        loop = 2,
                        pixels =
                            PixelAttributes(
                                channels = "RGBA".toPixelChannels(),
                                layout = PixelLayout.INTERLEAVED,
                                dataType = PixelDataType.UINT8,
                            ),
                    ),
            )

        attributes shouldBe expectedAttributes
        postgresJson.decodeFromString<ImageVariantAttributes>(attributes).toAttributes().orientation shouldBe 6
    }

    @Test
    fun `the same transformations generated based on the same parameters`() {
        val expectedTransformation =
            Json.encodeToString(
                ImageVariantTransformation(
                    width = 100,
                    height = 100,
                    format = ImageFormat.PIXELS,
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
                    pixels =
                        ImageVariantPixels(
                            channels = "RGBA",
                        ),
                ),
            )
        val transformations1 =
            VariantParameterGenerator.generateImageVariantTransformations(
                transformation =
                    Transformation(
                        height = 100.toDimension(),
                        width = 100.toDimension(),
                        format = ImageFormat.PIXELS,
                        fit = Fit.FILL,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.SEPIA,
                        blur = 10.toBlur(),
                        quality = 30.toQuality(),
                        padding =
                            PaddingTransformation(
                                amount = 10.toPaddingAmount(),
                                color = listOf(100, 100, 50, 10),
                            ),
                        metadata =
                            MetadataTransformation(
                                strip = setOf(MetadataType.XMP, MetadataType.IPTC),
                            ),
                        colorSpace = ColorSpace.P3,
                        pixels =
                            PixelTransformation(
                                channels = "RGBA".toPixelChannels(),
                            ),
                    ),
            )
        val transformations2 =
            VariantParameterGenerator.generateImageVariantTransformations(
                transformation =
                    Transformation(
                        height = 100.toDimension(),
                        width = 100.toDimension(),
                        format = ImageFormat.PIXELS,
                        fit = Fit.FILL,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.SEPIA,
                        blur = 10.toBlur(),
                        quality = 30.toQuality(),
                        padding =
                            PaddingTransformation(
                                amount = 10.toPaddingAmount(),
                                color = listOf(100, 100, 50, 10),
                            ),
                        metadata =
                            MetadataTransformation(
                                strip = setOf(MetadataType.IPTC, MetadataType.XMP),
                            ),
                        colorSpace = ColorSpace.P3,
                        pixels =
                            PixelTransformation(
                                channels = "RGBA".toPixelChannels(),
                            ),
                    ),
            )

        transformations1 shouldBe transformations2 shouldBe expectedTransformation
    }

    @Test
    fun `default fields are ignored when serializing`() {
        val transformation =
            Transformation(
                height = 100.toDimension(),
                width = 150.toDimension(),
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
                height = 100.toDimension(),
                width = 150.toDimension(),
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

    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `pixel layout survives persistence and original attribute conversion`(layout: PixelLayout) {
        val attributes =
            Attributes(
                width = 2.toDimension(),
                height = 2.toDimension(),
                format = ImageFormat.PIXELS,
                colorSpace = ColorSpace.SRGB,
                pixels = PixelAttributes("BGRA".toPixelChannels(), layout, PixelDataType.UINT8),
            )
        val transformation = Transformation.fromAttributes(attributes)

        transformation.pixels shouldBe PixelTransformation("BGRA".toPixelChannels(), layout)
        val serialized = VariantParameterGenerator.generateImageVariantTransformations(transformation)
        VariantParameterGenerator.generateImageVariantTransformations(attributes) shouldBe serialized
        postgresJson.decodeFromString<ImageVariantTransformation>(serialized).toTransformation() shouldBe transformation
    }

    @Test
    fun `planar and interleaved layouts have distinct cache identities`() {
        val interleaved =
            Transformation(
                width = 2.toDimension(),
                height = 2.toDimension(),
                format = ImageFormat.PIXELS,
                colorSpace = ColorSpace.SRGB,
                pixels = PixelTransformation("RGB".toPixelChannels(), PixelLayout.INTERLEAVED),
            )
        val planar = interleaved.copy(pixels = interleaved.pixels!!.copy(layout = PixelLayout.PLANAR))

        VariantParameterGenerator.generateImageVariantTransformations(planar) shouldNotBe
            VariantParameterGenerator.generateImageVariantTransformations(interleaved)
    }

    @Test
    fun `legacy raw pixel cache keys still decode and match interleaved requests`() {
        val legacy = """{"width":2,"height":2,"format":"pixels","pixels":{"channels":"RGB"}}"""
        val restored = postgresJson.decodeFromString<ImageVariantTransformation>(legacy).toTransformation()

        restored.pixels shouldBe PixelTransformation("RGB".toPixelChannels(), PixelLayout.INTERLEAVED)
        postgresJson.parseToJsonElement(VariantParameterGenerator.generateImageVariantTransformations(restored)) shouldBe
            postgresJson.parseToJsonElement(legacy)
    }
}
