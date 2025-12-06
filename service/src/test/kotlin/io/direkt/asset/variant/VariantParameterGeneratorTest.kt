package io.direkt.asset.variant

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.datastore.postgres.ImageVariantAttributes
import io.direkt.infrastructure.datastore.postgres.ImageVariantTransformation
import io.direkt.infrastructure.datastore.postgres.VariantParameterGenerator
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import net.openhft.hashing.LongHashFunction
import org.junit.jupiter.api.Test

class VariantParameterGeneratorTest {
    private val xx3 = LongHashFunction.xx3()

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
    fun `the same transformations and key are generated based on the same parameters`() {
        val expectedAttributes =
            Json.encodeToString(
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
        val expectedKey = xx3.hashBytes(expectedAttributes.toByteArray(Charsets.UTF_8))
        val (transformations1, key1) =
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
        val (transformations2, key2) =
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
        key1 shouldBe key2 shouldBe expectedKey
    }
}
