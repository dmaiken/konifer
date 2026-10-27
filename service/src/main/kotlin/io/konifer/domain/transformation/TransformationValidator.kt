package io.konifer.domain.transformation

import io.konifer.common.image.Rotate
import io.konifer.domain.variant.TransformationLimitProperties

object TransformationValidator {
    fun validateNormalizedTransformation(
        limits: TransformationLimitProperties,
        transformation: Transformation,
    ) {
        val (outputWidth, outputHeight) = calculateOutputDimensions(transformation)

        if (outputWidth > limits.maxWidth.value) {
            throw InvalidTransformationException("Width $outputWidth must not exceed ${limits.maxWidth.value}")
        }

        if (outputHeight > limits.maxHeight.value) {
            throw InvalidTransformationException("Height $outputHeight must not exceed ${limits.maxHeight.value}")
        }

        val pixels = outputWidth * outputHeight
        if (pixels > limits.maxPixels.value) {
            throw InvalidTransformationException("Output pixels $pixels must not exceed ${limits.maxPixels.value}")
        }
    }

    fun validateRequestedTransformation(
        limits: TransformationLimitProperties,
        requested: RequestedTransformation,
    ) {
        val (outputWidth, outputHeight) = calculateOutputDimensions(requested)
        if (outputHeight != null && outputHeight > limits.maxHeight.value) {
            throw InvalidTransformationException("height $outputHeight must not exceed ${limits.maxHeight.value} limit")
        }
        if (outputWidth != null && outputWidth > limits.maxWidth.value) {
            throw InvalidTransformationException("width $outputWidth must not exceed ${limits.maxWidth.value} limit")
        }
        if (outputHeight != null && outputWidth != null && outputHeight * outputWidth > limits.maxPixels.value) {
            throw InvalidTransformationException("max image size exceeds configured maxPixels: ${limits.maxPixels.value}")
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
