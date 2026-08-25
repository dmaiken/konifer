package io.konifer.domain.transformation

import io.konifer.common.image.Rotate
import io.konifer.domain.context.RequestedTransformation
import io.konifer.domain.variant.LimitProperties
import io.konifer.domain.variant.TransformProperties

object TransformationValidator {
    fun validateNormalizedTransformation(
        transformProperties: TransformProperties,
        transformation: Transformation,
    ) {
        if (transformation.originalVariant) return

        val limits = transformProperties.limits
        val (outputWidth, outputHeight) = calculateOutputDimensions(transformation)

        try {
            require(outputWidth <= limits.maxWidth.value) {
                "Width $outputWidth must not exceed ${limits.maxWidth.value}"
            }

            require(outputHeight <= limits.maxHeight.value) {
                "Height $outputHeight must not exceed ${limits.maxHeight.value}"
            }

            val pixels = outputWidth * outputHeight
            require(pixels <= limits.maxPixels.value) {
                "Output pixels $pixels must not exceed ${limits.maxPixels.value}"
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidTransformationException(e.message, e)
        }
    }

    fun validateRequestedTransformation(
        limits: LimitProperties,
        requested: RequestedTransformation,
    ) {
        val (outputWidth, outputHeight) = calculateOutputDimensions(requested)
        try {
            outputHeight?.let { height ->
                require(height <= limits.maxHeight.value) {
                    "height $height must not exceed ${limits.maxHeight.value} limit"
                }
            }
            outputWidth?.let { width ->
                require(width <= limits.maxWidth.value) {
                    "width $width must not exceed ${limits.maxWidth.value} limit"
                }
            }

            if (outputHeight != null && outputWidth != null) {
                require(outputHeight * outputWidth <= limits.maxPixels.value) {
                    "max image size exceeds configured maxPixels: ${limits.maxPixels.value}"
                }
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidTransformationException(e.message, e)
        }
    }

    private fun calculateOutputDimensions(requested: RequestedTransformation): Pair<Long?, Long?> {
        val contentWidth = requested.width?.value?.toLong()
        val contentHeight = requested.height?.value?.toLong()
        val (rotatedWidth, rotatedHeight) =
            when (requested.rotate) {
                Rotate.NINETY,
                Rotate.TWO_HUNDRED_SEVENTY,
                -> contentHeight to contentWidth
                // We don't know what kind of rotation will take place, so don't bother validating dimensions
                Rotate.AUTO -> null to null

                else -> contentWidth to contentHeight
            }
        val totalPadding = (requested.pad?.value?.toLong() ?: 0) * 2L

        return Pair(
            rotatedWidth?.plus(totalPadding),
            rotatedHeight?.plus(totalPadding),
        )
    }

    private fun calculateOutputDimensions(transformation: Transformation): Pair<Long, Long> {
        val contentWidth = transformation.width.value.toLong()
        val contentHeight = transformation.height.value.toLong()
        val (rotatedWidth, rotatedHeight) =
            when (transformation.rotate) {
                Rotate.NINETY,
                Rotate.TWO_HUNDRED_SEVENTY,
                -> contentHeight to contentWidth

                else -> contentWidth to contentHeight
            }
        val totalPadding =
            transformation.padding.amount.value
                .toLong() * 2L

        return Pair(
            rotatedWidth + totalPadding,
            rotatedHeight + totalPadding,
        )
    }
}
