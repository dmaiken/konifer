package io.konifer.domain.transformation

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.domain.variant.attribute.Attributes
import kotlinx.coroutines.Deferred

object ColorspaceNormalizer {
    suspend fun normalizeColorSpace(
        requested: RequestedTransformation,
        normalizedFormat: ImageFormat,
        originalAttributesDeferred: Deferred<Attributes>,
    ): ColorSpace {
        if (normalizedFormat == ImageFormat.PIXELS && requested.pixelChannels == PixelChannels.grayscale &&
            requested.colorSpace == TransformableColorSpace.ORIGIN
        ) {
            // Force greyscale colorspace conversion
            return ColorSpace.Grayscale
        }

        return when (requested.colorSpace) {
            TransformableColorSpace.ORIGIN -> originalAttributesDeferred.await().colorSpace
            TransformableColorSpace.P3 -> ColorSpace.P3
            TransformableColorSpace.SRGB -> ColorSpace.SRGB
            TransformableColorSpace.GRAYSCALE -> ColorSpace.Grayscale
        }
    }
}
