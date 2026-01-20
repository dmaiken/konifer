package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.Transformation
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class VariantParameterGeneratorTest {
    @Test
    fun `can generate variant attributes`() {
        val expectedAttributes =
            Json.Default.encodeToString(
                ImageVariantAttributes(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                ),
            )
        val attributes =
            VariantParameterGenerator.generateImageVariantAttributes(
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
    fun `the same transformations generated based on the same parameters`() {
        val expectedAttributes =
            Json.Default.encodeToString(
                ImageVariantTransformation(
                    width = 100,
                    height = 100,
                    format = ImageFormat.JPEG,
                    fit = Fit.FILL,
                    gravity = Gravity.ENTROPY,
                    rotate = Rotate.ONE_HUNDRED_EIGHTY,
                    horizontalFlip = true,
                    filter = Filter.GREYSCALE,
                    blur = 10,
                    quality = 30,
                    pad = 10,
                    background = listOf(100, 100, 50, 10),
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
                        filter = Filter.GREYSCALE,
                        blur = 10,
                        quality = 30,
                        pad = 10,
                        background = listOf(100, 100, 50, 10),
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
                        filter = Filter.GREYSCALE,
                        blur = 10,
                        quality = 30,
                        pad = 10,
                        background = listOf(100, 100, 50, 10),
                    ),
            )

        transformations1 shouldBe transformations2 shouldBe expectedAttributes
    }
}
