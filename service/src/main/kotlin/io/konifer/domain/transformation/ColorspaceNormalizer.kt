package io.konifer.domain.transformation

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.domain.variant.attribute.Attributes

object ColorspaceNormalizer {
    fun normalizeColorSpace(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
        originalVariantAttributes: Attributes,
    ): ColorSpace {
        if (normalizedFormat == ImageFormat.PIXELS && requested.pixelChannels == PixelChannels.grayscale &&
            requested.colorSpace == TransformableColorSpace.ORIGIN
        ) {
            // Force greyscale colorspace conversion
            return ColorSpace.Grayscale
        }

        return when (requested.colorSpace) {
            TransformableColorSpace.ORIGIN -> originalVariantAttributes.colorSpace
            TransformableColorSpace.P3 -> ColorSpace.P3
            TransformableColorSpace.SRGB -> ColorSpace.SRGB
            TransformableColorSpace.GRAYSCALE -> ColorSpace.Grayscale
        }
    }
}
