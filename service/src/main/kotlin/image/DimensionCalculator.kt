package io.image

import app.photofox.vipsffm.VImage
import io.asset.variant.ImageVariantAttributes
import io.image.model.Fit

object DimensionCalculator {

    fun calculateDimensions(
        image: VImage,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> = calculateDimensions(
        sourceWidth = image.width,
        sourceHeight = image.height,
        width = width,
        height = height,
        fit = fit,
    )

    fun calculateDimensions(
        originalAttributes: ImageVariantAttributes?,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> = calculateDimensions(
        sourceWidth = originalAttributes?.width,
        sourceHeight = originalAttributes?.height,
        width = width,
        height = height,
        fit = fit,
    )

    private fun calculateDimensions(
        sourceWidth: Int?,
        sourceHeight: Int?,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> = when (fit) {
        Fit.SCALE -> {
            val widthRatio =
                width?.let {
                    it.toDouble() / requireNotNull(sourceWidth)
                } ?: 1.0
            val heightRatio =
                height?.let {
                    it.toDouble() / requireNotNull(sourceHeight)
                } ?: 1.0
            Pair((sourceWidth!! * widthRatio).toInt(), (sourceHeight!! * heightRatio).toInt())
        }
        Fit.FIT, Fit.STRETCH ->
            Pair(
                requireNotNull(width) {
                    "Width must be specified if fit is '${Fit.FIT.name.lowercase()}' or '${Fit.STRETCH.name.lowercase()}'"
                },
                requireNotNull(height) {
                    "Height must be specified if fit is '${Fit.FIT.name.lowercase()}' or '${Fit.STRETCH.name.lowercase()}'"
                },
            )
    }
}