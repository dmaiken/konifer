package io.konifer.domain.transformation

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.LimitProperties
import io.konifer.domain.variant.TransformProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TransformationValidatorTest {
    @Test
    fun `original variant bypasses transformation limits`() {
        shouldNotThrowAny {
            TransformationValidator.validateNormalizedTransformation(
                transformProperties = transformProperties(maxWidth = 0, maxHeight = 0, maxPixels = 0),
                transformation = Transformation.ORIGINAL_VARIANT,
            )
        }
    }

    @Test
    fun `dimensions and pixels can equal their limits`() {
        shouldNotThrowAny {
            TransformationValidator.validateNormalizedTransformation(
                transformProperties = transformProperties(maxWidth = 100, maxHeight = 200, maxPixels = 20_000),
                transformation = transformation(width = 100, height = 200),
            )
        }
    }

    @Test
    fun `width cannot exceed its limit`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                TransformationValidator.validateNormalizedTransformation(
                    transformProperties = transformProperties(maxWidth = 99, maxHeight = 200, maxPixels = 20_000),
                    transformation = transformation(width = 100, height = 200),
                )
            }

        exception.message shouldBe "Width 100 must not exceed 99"
    }

    @Test
    fun `height cannot exceed its limit`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                TransformationValidator.validateNormalizedTransformation(
                    transformProperties = transformProperties(maxWidth = 100, maxHeight = 199, maxPixels = 20_000),
                    transformation = transformation(width = 100, height = 200),
                )
            }

        exception.message shouldBe "Height 200 must not exceed 199"
    }

    @Test
    fun `output pixels cannot exceed their limit`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                TransformationValidator.validateNormalizedTransformation(
                    transformProperties = transformProperties(maxWidth = 100, maxHeight = 100, maxPixels = 9_999),
                    transformation = transformation(width = 100, height = 100),
                )
            }

        exception.message shouldBe "Output pixels 10000 must not exceed 9999"
    }

    @Test
    fun `padding is included in output dimensions`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                TransformationValidator.validateNormalizedTransformation(
                    transformProperties = transformProperties(maxWidth = 100, maxHeight = 100, maxPixels = 10_000),
                    transformation = transformation(width = 95, height = 90, padding = 3),
                )
            }

        exception.message shouldBe "Width 101 must not exceed 100"
    }

    @ParameterizedTest
    @EnumSource(Rotate::class, names = ["NINETY", "TWO_HUNDRED_SEVENTY"])
    fun `quarter turns swap dimensions before applying padding`(rotate: Rotate) {
        shouldNotThrowAny {
            TransformationValidator.validateNormalizedTransformation(
                transformProperties = transformProperties(maxWidth = 60, maxHeight = 110, maxPixels = 6_600),
                transformation = transformation(width = 100, height = 50, padding = 5, rotate = rotate),
            )
        }
    }

    private fun transformProperties(
        maxWidth: Int,
        maxHeight: Int,
        maxPixels: Long,
    ): TransformProperties =
        TransformProperties(
            limits =
                LimitProperties(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    maxPixels = maxPixels,
                ),
        )

    private fun transformation(
        width: Int,
        height: Int,
        padding: Int = 0,
        rotate: Rotate = Rotate.ZERO,
    ): Transformation =
        Transformation(
            width = width.toDimension(),
            height = height.toDimension(),
            format = ImageFormat.PNG,
            rotate = rotate,
            padding =
                PaddingTransformation(
                    amount = padding.toPaddingAmount(),
                    color = emptyList(),
                ),
            colorSpace = ColorSpace.SRGB,
        )
}
