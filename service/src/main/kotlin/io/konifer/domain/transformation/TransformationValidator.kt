package io.konifer.domain.transformation

import io.konifer.common.image.Rotate
import io.konifer.domain.variant.TransformProperties

object TransformationValidator {
    fun validateNormalizedTransformation(
        transformProperties: TransformProperties,
        transformation: Transformation,
    ) {
        if (transformation.originalVariant) return

        val limits = transformProperties.limits
        val (outputWidth, outputHeight) = calculateOutputDimensions(transformation)

        require(outputWidth <= limits.maxWidth) {
            "Width $outputWidth must not exceed ${limits.maxWidth}"
        }

        require(outputHeight <= limits.maxHeight) {
            "Height $outputHeight must not exceed ${limits.maxHeight}"
        }

        val pixels = outputWidth * outputHeight
        require(pixels <= limits.maxPixels) {
            "Output pixels $pixels must not exceed ${limits.maxPixels}"
        }
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
