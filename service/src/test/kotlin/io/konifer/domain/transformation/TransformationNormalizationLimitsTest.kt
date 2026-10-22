package io.konifer.domain.transformation

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.toPixelCount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TransformationNormalizationLimitsTest {
    private val normalizer = TransformationNormalizer()
    private val original =
        Attributes(
            width = 100.toDimension(),
            height = 200.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )
    private val limits =
        TransformationLimitProperties(
            maxWidth = 100.toDimension(),
            maxHeight = 200.toDimension(),
            maxPixels = 20_000L.toPixelCount(),
        )

    @Test
    fun `derived dimensions and pixels can equal supplied limits`() =
        runTest {
            val normalized = normalizer.normalize(RequestedTransformation(width = 100.toDimension()), original, limits)

            normalized.width.value shouldBe 100
            normalized.height.value shouldBe 200
            normalized.format shouldBe ImageFormat.PNG
        }

    @Test
    fun `derived width is rejected before normalization returns`() =
        runTest {
            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(
                        RequestedTransformation(height = 200.toDimension()),
                        original,
                        limits.copy(maxWidth = 99.toDimension()),
                    )
                }

            exception.message shouldBe "Width 100 must not exceed 99"
        }

    @Test
    fun `derived height is rejected before normalization returns`() =
        runTest {
            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(
                        RequestedTransformation(width = 100.toDimension()),
                        original,
                        limits.copy(maxHeight = 199.toDimension()),
                    )
                }

            exception.message shouldBe "Height 200 must not exceed 199"
        }

    @Test
    fun `derived pixel count is rejected before normalization returns`() =
        runTest {
            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(
                        RequestedTransformation(width = 100.toDimension()),
                        original,
                        limits.copy(maxPixels = 19_999L.toPixelCount()),
                    )
                }

            exception.message shouldBe "Output pixels 20000 must not exceed 19999"
        }

    @Test
    fun `padding is validated after dimensions are derived`() =
        runTest {
            val requested = RequestedTransformation(width = 100.toDimension(), pad = 1.toPaddingAmount())
            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(requested, original, limits)
                }

            exception.message shouldBe "Width 102 must not exceed 100"
        }

    @ParameterizedTest
    @EnumSource(Rotate::class, names = ["NINETY", "TWO_HUNDRED_SEVENTY"])
    fun `resolved rotation and padding respect inclusive output limits`(rotate: Rotate) =
        runTest {
            val rotatedLimits =
                TransformationLimitProperties(
                    maxWidth = 60.toDimension(),
                    maxHeight = 110.toDimension(),
                    maxPixels = 6_600L.toPixelCount(),
                )
            val normalized =
                normalizer.normalize(
                    RequestedTransformation(
                        width = 100.toDimension(),
                        height = 50.toDimension(),
                        rotate = rotate,
                        pad = 5.toPaddingAmount(),
                    ),
                    original,
                    rotatedLimits,
                )

            normalized.rotate shouldBe rotate
            normalized.padding.amount.value shouldBe 5
        }

    @Test
    fun `auto rotation is resolved before enforcing output limits`() =
        runTest {
            val requested = RequestedTransformation(width = 40.toDimension(), height = 20.toDimension(), rotate = Rotate.AUTO)
            val rotatedLimits =
                TransformationLimitProperties(
                    maxWidth = 20.toDimension(),
                    maxHeight = 40.toDimension(),
                    maxPixels = 800L.toPixelCount(),
                )
            val orientedOriginal = original.copy(orientation = 6)
            normalizer.normalize(requested, orientedOriginal, rotatedLimits).rotate shouldBe Rotate.NINETY

            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(requested, orientedOriginal, rotatedLimits.copy(maxHeight = 39.toDimension()))
                }
            exception.message shouldBe "Height 40 must not exceed 39"
        }

    @Test
    fun `batch preserves order and validates every transformation`() =
        runTest {
            val normalized =
                normalizer.normalize(
                    listOf(RequestedTransformation(width = 50.toDimension()), RequestedTransformation(width = 100.toDimension())),
                    original,
                    limits,
                )

            normalized.map { it.width.value } shouldContainExactly listOf(50, 100)
            normalized.map { it.height.value } shouldContainExactly listOf(100, 200)
        }

    @Test
    fun `batch throws if a later transformation violates limits`() =
        runTest {
            val exception =
                shouldThrow<InvalidTransformationException> {
                    normalizer.normalize(
                        listOf(RequestedTransformation(width = 50.toDimension()), RequestedTransformation(width = 101.toDimension())),
                        original,
                        limits,
                    )
                }

            exception.message shouldBe "Width 101 must not exceed 100"
        }

    @Test
    fun `empty batch returns empty without validating source dimensions as output`() =
        runTest {
            normalizer.normalize(emptyList(), original, limits.copy(maxHeight = 1.toDimension())).shouldBeEmpty()
        }
}
