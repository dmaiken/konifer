package io.konifer.domain.transformation.pixel

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.RequestedTransformation

object PixelTransformationNormalizer {
    fun normalizePixelTransformation(
        requested: RequestedTransformation,
        normalizedColorSpace: ColorSpace,
        normalizedFormat: ImageFormat,
    ): PixelTransformation? {
        if (normalizedFormat != ImageFormat.PIXELS) return null

        if (normalizedColorSpace == ColorSpace.Grayscale) {
            throw IllegalArgumentException(
                "Cannot convert image with colorspace: ${normalizedColorSpace.name} to ${normalizedFormat.format} format",
            )
        }

        return PixelTransformation(
            channels = requested.pixelChannels ?: PixelChannels.default,
        )
    }
}
