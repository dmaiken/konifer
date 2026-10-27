package io.konifer.domain.transformation.pixel

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
import io.konifer.domain.transformation.InvalidTransformationException
import io.konifer.domain.transformation.RequestedTransformation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class PixelTransformationNormalizerTest {
    @ParameterizedTest
    @EnumSource(PixelLayout::class)
    fun `preserves requested layout for color pixels and normalizes grayscale to interleaved`(layout: PixelLayout) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(pixelLayout = layout),
            normalizedColorSpace = ColorSpace.SRGB,
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = true,
        ) shouldBe PixelTransformation(layout = layout)

        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(pixelChannels = PixelChannels.grayscale, pixelLayout = layout),
            normalizedColorSpace = ColorSpace.Grayscale,
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = true,
        ) shouldBe PixelTransformation(PixelChannels.grayscale, PixelLayout.INTERLEAVED)
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS"])
    fun `ignores requested pixel channels and layout for other output formats`(format: ImageFormat) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested =
                RequestedTransformation(
                    format = format,
                    pixelChannels = "BGRA".toPixelChannels(),
                    pixelLayout = PixelLayout.PLANAR,
                ),
            normalizedColorSpace = ColorSpace.Grayscale,
            normalizedFormat = format,
            isColorspaceLocked = true,
        ) shouldBe null
    }

    @Test
    fun `defaults to RGB layout when normalized output format is pixels`() {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(),
            normalizedColorSpace = ColorSpace.SRGB,
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = true,
        ) shouldBe PixelTransformation(channels = "RGB".toPixelChannels())
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "RGBA", "BGR", "BGRA", "ARGB", "ABGR"])
    fun `preserves requested pixel layout`(layout: String) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(pixelChannels = layout.toPixelChannels()),
            normalizedColorSpace = ColorSpace.P3,
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = true,
        ) shouldBe PixelTransformation(channels = layout.toPixelChannels())
    }

    @ParameterizedTest
    @ValueSource(strings = ["cymk", "unknown", "grayscale", "adobe_rgb", "custom"])
    fun `rejects normalized unsupported color space for pixels`(unsupportedColorSpace: String) {
        shouldThrow<InvalidTransformationException> {
            PixelTransformationNormalizer.normalizePixelTransformation(
                requested =
                    RequestedTransformation(
                        format = ImageFormat.PIXELS,
                        pixelChannels = "RGBA".toPixelChannels(),
                    ),
                normalizedColorSpace = unsupportedColorSpace.toColorSpace(),
                normalizedFormat = ImageFormat.PIXELS,
                isColorspaceLocked = true,
            )
        }.message shouldBe "Cannot convert image with colorspace: $unsupportedColorSpace to pixels format"
    }

    @ParameterizedTest
    @ValueSource(strings = ["srgb", "p3", "grayscale", "cymk", "adobe_rgb"])
    fun `allows grayscale channels with an unlocked source color space`(colorSpace: String) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(colorSpace = TransformableColorSpace.ORIGIN, pixelChannels = PixelChannels.grayscale),
            normalizedColorSpace = colorSpace.toColorSpace(),
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = false,
        ) shouldBe PixelTransformation(channels = PixelChannels.grayscale)
    }

    @Test
    fun `allows grayscale channels with a locked grayscale color space`() {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(colorSpace = TransformableColorSpace.GRAYSCALE, pixelChannels = PixelChannels.grayscale),
            normalizedColorSpace = ColorSpace.Grayscale,
            normalizedFormat = ImageFormat.PIXELS,
            isColorspaceLocked = true,
        ) shouldBe PixelTransformation(channels = PixelChannels.grayscale)
    }

    @ParameterizedTest
    @ValueSource(strings = ["srgb", "p3", "cymk", "adobe_rgb", "unknown", "custom"])
    fun `rejects grayscale channels with a locked non grayscale color space`(colorSpace: String) {
        shouldThrow<InvalidTransformationException> {
            PixelTransformationNormalizer.normalizePixelTransformation(
                requested = RequestedTransformation(pixelChannels = PixelChannels.grayscale),
                normalizedColorSpace = colorSpace.toColorSpace(),
                normalizedFormat = ImageFormat.PIXELS,
                isColorspaceLocked = true,
            )
        }.message shouldBe "Cannot request a colorspace other than grayscale or origin when pixel-channels is GRAY"
    }

    @Test
    fun `rejects default RGB channels for a grayscale source`() {
        shouldThrow<InvalidTransformationException> {
            PixelTransformationNormalizer.normalizePixelTransformation(
                requested = RequestedTransformation(colorSpace = TransformableColorSpace.ORIGIN),
                normalizedColorSpace = ColorSpace.Grayscale,
                normalizedFormat = ImageFormat.PIXELS,
                isColorspaceLocked = false,
            )
        }.message shouldBe "Cannot convert image with colorspace: grayscale to pixels format"
    }

    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS"])
    fun `ignores grayscale channel constraints for other output formats`(format: ImageFormat) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(format = format, pixelChannels = PixelChannels.grayscale, pixelLayout = PixelLayout.PLANAR),
            normalizedColorSpace = ColorSpace.SRGB,
            normalizedFormat = format,
            isColorspaceLocked = true,
        ) shouldBe null
    }
}
