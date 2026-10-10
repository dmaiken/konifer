package io.konifer.domain.transformation.pixel

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.RequestedTransformation

object PixelTransformationNormalizer {
    private val allowedColorSpaces = setOf(ColorSpace.SRGB, ColorSpace.P3)

    fun normalizePixelTransformation(
        requested: RequestedTransformation,
        normalizedColorSpace: ColorSpace,
        normalizedFormat: ImageFormat,
    ): PixelTransformation? {
        if (normalizedFormat != ImageFormat.PIXELS) return null

        if (normalizedColorSpace !in allowedColorSpaces) {
            throw IllegalArgumentException(
                "Cannot convert image with colorspace: ${normalizedColorSpace.name} to ${normalizedFormat.format} format",
            )
        }

        return PixelTransformation(
            channels = requested.pixelChannels ?: PixelChannels.default,
        )
    }
}
