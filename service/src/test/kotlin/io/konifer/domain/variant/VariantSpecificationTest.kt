package io.konifer.domain.variant

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.attribute.Attributes
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VariantSpecificationTest {
    private val attributes =
        Attributes(
            width = 100.toDimension(),
            height = 80.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )

    @Test
    fun `original selection matches original identity only`() {
        VariantSpecification.Original.matches(VariantSpecification.Original) { attributes } shouldBe true
        VariantSpecification.Original.matches(
            VariantSpecification.Transformed(Transformation.fromAttributes(attributes)),
        ) { attributes } shouldBe false
    }

    @Test
    fun `transformed selection can match original attributes`() {
        val transformation = Transformation.fromAttributes(attributes)
        VariantSpecification.Transformed(transformation).matches(VariantSpecification.Original) { attributes } shouldBe true
        VariantSpecification
            .Transformed(transformation.copy(width = 50.toDimension()))
            .matches(VariantSpecification.Original) { attributes } shouldBe false
    }

    @Test
    fun `matching ignores execution flags and metadata set order`() {
        val transformation =
            Transformation.fromAttributes(attributes).copy(
                metadata = MetadataTransformation(linkedSetOf(MetadataType.XMP, MetadataType.EXIF)),
            )
        val requested =
            transformation.copy(
                canUpscale = false,
                isAutoRotate = true,
                isColorSpaceLocked = true,
                metadata = MetadataTransformation(linkedSetOf(MetadataType.EXIF, MetadataType.XMP)),
            )
        VariantSpecification.Transformed(requested).matches(VariantSpecification.Transformed(transformation)) { attributes } shouldBe true
    }

    @Test
    fun `matching preserves pixel channel order and layout`() {
        val transformation =
            Transformation(
                width = 2.toDimension(),
                height = 2.toDimension(),
                format = ImageFormat.PIXELS,
                colorSpace = ColorSpace.SRGB,
                pixels = PixelTransformation("RGB".toPixelChannels(), PixelLayout.PLANAR),
            )
        val stored = VariantSpecification.Transformed(transformation)
        VariantSpecification
            .Transformed(transformation.copy(pixels = PixelTransformation("BGR".toPixelChannels(), PixelLayout.PLANAR)))
            .matches(stored) { attributes } shouldBe false
        VariantSpecification
            .Transformed(
                transformation.copy(pixels = PixelTransformation("RGB".toPixelChannels(), PixelLayout.INTERLEAVED)),
            ).matches(stored) { attributes } shouldBe false
    }

    @Test
    fun `original selection never reads attributes`() {
        val storedSpecifications =
            listOf(
                VariantSpecification.Original,
                VariantSpecification.Transformed(Transformation.fromAttributes(attributes)),
            )

        storedSpecifications.forEach { stored ->
            VariantSpecification.Original.matches(stored) {
                error("Original selection must not read attributes")
            } shouldBe (stored == VariantSpecification.Original)
        }
    }

    @Test
    fun `transformed comparisons never read attributes`() {
        val transformation = Transformation.fromAttributes(attributes)
        val requested = VariantSpecification.Transformed(transformation)

        requested.matches(VariantSpecification.Transformed(transformation)) {
            error("Transformed comparisons must not read attributes")
        } shouldBe true
        requested.matches(VariantSpecification.Transformed(transformation.copy(width = 50.toDimension()))) {
            error("Transformed comparisons must not read attributes")
        } shouldBe false
    }

    @Test
    fun `transformed selection reads original attributes exactly once per comparison`() {
        val transformation = Transformation.fromAttributes(attributes)
        val requestedTransformations = listOf(transformation, transformation.copy(width = 50.toDimension()))

        requestedTransformations.forEach { requested ->
            var reads = 0

            VariantSpecification.Transformed(requested).matches(VariantSpecification.Original) {
                reads++
                attributes
            } shouldBe (requested == transformation)
            reads shouldBe 1
        }
    }
}
