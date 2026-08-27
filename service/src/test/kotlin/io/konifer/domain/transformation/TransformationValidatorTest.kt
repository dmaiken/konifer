package io.konifer.domain.transformation

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.TransformProperties
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.toPixelCount
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
                transformProperties = transformProperties(maxWidth = 1, maxHeight = 1, maxPixels = 1),
                transformation = transformation(width = 100, height = 200).copy(originalVariant = true),
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
            shouldThrow<InvalidTransformationException> {
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
            shouldThrow<InvalidTransformationException> {
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
            shouldThrow<InvalidTransformationException> {
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
            shouldThrow<InvalidTransformationException> {
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

    @Test
    fun `requested dimensions and pixels can equal their limits`() {
        shouldNotThrowAny {
            TransformationValidator.validateRequestedTransformation(
                limits =
                    TransformationLimitProperties(
                        maxWidth = 100.toDimension(),
                        maxHeight = 200.toDimension(),
                        maxPixels = 20_000L.toPixelCount(),
                    ),
                requested = createRequestedImageTransformation(width = 100, height = 200),
            )
        }
    }

    @Test
    fun `known requested width is validated when height is unknown`() {
        val exception =
            shouldThrow<InvalidTransformationException> {
                TransformationValidator.validateRequestedTransformation(
                    limits =
                        TransformationLimitProperties(
                            maxWidth = 99.toDimension(),
                            maxHeight = 200.toDimension(),
                            maxPixels = 20_000L.toPixelCount(),
                        ),
                    requested = createRequestedImageTransformation(width = 100),
                )
            }

        exception.message shouldBe "width 100 must not exceed 99 limit"
    }

    @Test
    fun `known requested height is validated when width is unknown`() {
        val exception =
            shouldThrow<InvalidTransformationException> {
                TransformationValidator.validateRequestedTransformation(
                    limits =
                        TransformationLimitProperties(
                            maxWidth = 100.toDimension(),
                            maxHeight = 199.toDimension(),
                            maxPixels = 20_000L.toPixelCount(),
                        ),
                    requested = createRequestedImageTransformation(height = 200),
                )
            }

        exception.message shouldBe "height 200 must not exceed 199 limit"
    }

    @Test
    fun `requested pixels are validated when both dimensions are known`() {
        val exception =
            shouldThrow<InvalidTransformationException> {
                TransformationValidator.validateRequestedTransformation(
                    limits =
                        TransformationLimitProperties(
                            maxWidth = 100.toDimension(),
                            maxHeight = 100.toDimension(),
                            maxPixels = 9_999L.toPixelCount(),
                        ),
                    requested = createRequestedImageTransformation(width = 100, height = 100),
                )
            }

        exception.message shouldBe "max image size exceeds configured maxPixels: 9999"
    }

    @Test
    fun `requested padding is included in known output dimensions`() {
        val exception =
            shouldThrow<InvalidTransformationException> {
                TransformationValidator.validateRequestedTransformation(
                    limits =
                        TransformationLimitProperties(
                            maxWidth = 100.toDimension(),
                            maxHeight = 100.toDimension(),
                            maxPixels = 10_000L.toPixelCount(),
                        ),
                    requested = createRequestedImageTransformation(width = 95, height = 90, pad = 3),
                )
            }

        exception.message shouldBe "width 101 must not exceed 100 limit"
    }

    @ParameterizedTest
    @EnumSource(Rotate::class, names = ["NINETY", "TWO_HUNDRED_SEVENTY"])
    fun `requested quarter turns swap known dimensions before applying padding`(rotate: Rotate) {
        shouldNotThrowAny {
            TransformationValidator.validateRequestedTransformation(
                limits =
                    TransformationLimitProperties(
                        maxWidth = 60.toDimension(),
                        maxHeight = 110.toDimension(),
                        maxPixels = 6_600L.toPixelCount(),
                    ),
                requested = createRequestedImageTransformation(width = 100, height = 50, pad = 5, rotate = rotate),
            )
        }
    }

    @Test
    fun `requested auto rotation defers dimension validation`() {
        shouldNotThrowAny {
            TransformationValidator.validateRequestedTransformation(
                limits =
                    TransformationLimitProperties(
                        maxWidth = 1.toDimension(),
                        maxHeight = 1.toDimension(),
                        maxPixels = 1L.toPixelCount(),
                    ),
                requested = createRequestedImageTransformation(width = 100, height = 200, rotate = Rotate.AUTO),
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
                TransformationLimitProperties(
                    maxWidth = maxWidth.toDimension(),
                    maxHeight = maxHeight.toDimension(),
                    maxPixels = maxPixels.toPixelCount(),
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
