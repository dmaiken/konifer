package io.konifer.domain.transformation.pixel

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.toColorSpace
import io.konifer.domain.transformation.RequestedTransformation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class PixelTransformationNormalizerTest {
    @ParameterizedTest
    @EnumSource(ImageFormat::class, mode = EnumSource.Mode.EXCLUDE, names = ["PIXELS"])
    fun `does not normalize pixel layout for other output formats`(format: ImageFormat) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(format = ImageFormat.PIXELS, pixelChannels = "BGRA".toPixelChannels()),
            normalizedColorSpace = ColorSpace.Grayscale,
            normalizedFormat = format,
        ) shouldBe null
    }

    @Test
    fun `defaults to RGB layout when normalized output format is pixels`() {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(),
            normalizedColorSpace = ColorSpace.SRGB,
            normalizedFormat = ImageFormat.PIXELS,
        ) shouldBe PixelTransformation(channels = "RGB".toPixelChannels())
    }

    @ParameterizedTest
    @ValueSource(strings = ["RGB", "RGBA", "BGR", "BGRA", "ARGB", "ABGR"])
    fun `preserves requested pixel layout`(layout: String) {
        PixelTransformationNormalizer.normalizePixelTransformation(
            requested = RequestedTransformation(pixelChannels = layout.toPixelChannels()),
            normalizedColorSpace = ColorSpace.P3,
            normalizedFormat = ImageFormat.PIXELS,
        ) shouldBe PixelTransformation(channels = layout.toPixelChannels())
    }

    @ParameterizedTest
    @ValueSource(strings = ["cymk", "unknown", "grayscale", "adobe_rgb", "custom"])
    fun `rejects normalized unsupported color space for pixels`(unsupportedColorSpace: String) {
        shouldThrow<IllegalArgumentException> {
            PixelTransformationNormalizer.normalizePixelTransformation(
                requested =
                    RequestedTransformation(
                        format = ImageFormat.PIXELS,
                        pixelChannels = "RGBA".toPixelChannels(),
                    ),
                normalizedColorSpace = unsupportedColorSpace.toColorSpace(),
                normalizedFormat = ImageFormat.PIXELS,
            )
        }.message shouldBe "Cannot convert image with colorspace: $unsupportedColorSpace to pixels format"
    }
}
