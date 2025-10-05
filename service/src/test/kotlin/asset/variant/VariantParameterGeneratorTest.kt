package io.asset.variant

import asset.variant.ImageVariantTransformation
import asset.variant.VariantParameterGenerator
import image.model.Attributes
import image.model.ImageFormat
import image.model.Transformation
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.Rotate
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import net.openhft.hashing.LongHashFunction
import org.junit.jupiter.api.Test

class VariantParameterGeneratorTest {
    private val xx3 = LongHashFunction.xx3()
    private val variantParameterGenerator = VariantParameterGenerator()

    @Test
    fun `can generate variant attributes and key`() {
        val expectedAttributes =
            Json.encodeToString(
                ImageVariantAttributes(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                ),
            )
        val attributes =
            variantParameterGenerator.generateImageVariantAttributes(
                imageAttributes =
                    Attributes(
                        width = 100,
                        height = 100,
                        format = ImageFormat.JPEG,
                    ),
            )

        attributes shouldBe expectedAttributes
    }

    @Test
    fun `the same transformations and key are generated based on the same parameters`() {
        val expectedAttributes =
            Json.encodeToString(
                ImageVariantTransformation(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                    fit = Fit.FIT,
                    gravity = Gravity.ENTROPY,
                    rotate = Rotate.ONE_HUNDRED_EIGHTY,
                    horizontalFlip = true,
                    filter = Filter.GREYSCALE,
                ),
            )
        val expectedKey = xx3.hashBytes(expectedAttributes.toByteArray(Charsets.UTF_8))
        val (transformations1, key1) =
            variantParameterGenerator.generateImageVariantTransformations(
                imageTransformation =
                    Transformation(
                        height = 100,
                        width = 100,
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.GREYSCALE,
                    ),
            )
        val (transformations2, key2) =
            variantParameterGenerator.generateImageVariantTransformations(
                imageTransformation =
                    Transformation(
                        height = 100,
                        width = 100,
                        format = ImageFormat.JPEG,
                        fit = Fit.FIT,
                        gravity = Gravity.ENTROPY,
                        rotate = Rotate.ONE_HUNDRED_EIGHTY,
                        horizontalFlip = true,
                        filter = Filter.GREYSCALE,
                    ),
            )

        transformations1 shouldBe transformations2 shouldBe expectedAttributes
        key1 shouldBe key2 shouldBe expectedKey
    }
}
