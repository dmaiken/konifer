package io.konifer.domain.transformation.pixel

import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ManipulationParameters.PIXEL_CHANNELS
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.RequestedTransformation

object PixelTransformationNormalizer {
    private val allowedColorSpacesForColorChannels = setOf(ColorSpace.SRGB, ColorSpace.P3)

    fun normalizePixelTransformation(
        requested: RequestedTransformation,
        normalizedColorSpace: ColorSpace,
        normalizedFormat: ImageFormat,
        isColorspaceLocked: Boolean,
    ): PixelTransformation? {
        if (normalizedFormat != ImageFormat.PIXELS) return null

        // Even if image has origin colorspace of disallowed colorspace, we cannot perform this transformation
        // Ex: how do we populate color channels for a greyscale image?
        if (requested.pixelChannels != PixelChannels.grayscale && normalizedColorSpace !in allowedColorSpacesForColorChannels) {
            throw IllegalArgumentException(
                "Cannot convert image with colorspace: ${normalizedColorSpace.name} to ${normalizedFormat.format} format",
            )
        }
        if (requested.pixelChannels == PixelChannels.grayscale && isColorspaceLocked && normalizedColorSpace != ColorSpace.Grayscale) {
            throw IllegalArgumentException(
                "Cannot request a colorspace other than ${TransformableColorSpace.GRAYSCALE.queryParameterValue} or " +
                    "${TransformableColorSpace.ORIGIN.queryParameterValue} when $PIXEL_CHANNELS is ${requested.pixelChannels.value}",
            )
        }

        return PixelTransformation(
            channels = requested.pixelChannels ?: PixelChannels.default,
        )
    }
}
