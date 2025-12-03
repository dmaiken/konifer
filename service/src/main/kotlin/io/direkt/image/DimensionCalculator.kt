package io.image

import app.photofox.vipsffm.VImage
import io.image.model.Fit
import io.image.vips.pageSafeHeight
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

object DimensionCalculator {
    fun calculateDimensions(
        image: VImage,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> =
        calculateDimensions(
            sourceWidth = image.width,
            sourceHeight = image.pageSafeHeight(),
            width = width,
            height = height,
            fit = fit,
        )

    fun calculateDimensions(
        bufferedImage: BufferedImage,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> =
        calculateDimensions(
            sourceWidth = bufferedImage.width,
            sourceHeight = bufferedImage.height,
            width = width,
            height = height,
            fit = fit,
        )

    private fun calculateDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> =
        when (fit) {
            Fit.FIT -> {
                when {
                    width != null && height == null -> {
                        // Width specified, calculate height
                        val scale = width.toDouble() / sourceWidth
                        Pair(width, (sourceHeight * scale).roundToInt())
                    }
                    width == null && height != null -> {
                        // Height specified, calculate width
                        val scale = height.toDouble() / sourceHeight
                        Pair((sourceWidth * scale).roundToInt(), height)
                    }
                    width != null && height != null -> {
                        // Both specified: scale to fit inside box while preserving aspect ratio
                        val scale = minOf(width.toDouble() / sourceWidth, height.toDouble() / sourceHeight)
                        Pair((sourceWidth * scale).roundToInt(), (sourceHeight * scale).roundToInt())
                    }
                    else -> Pair(sourceWidth, sourceHeight)
                }
            }
            Fit.FILL, Fit.STRETCH, Fit.CROP ->
                Pair(
                    requireNotNull(width) {
                        "Width must be specified if fit is '${Fit.FILL.name.lowercase()}', " +
                            "'${Fit.STRETCH.name.lowercase()}', or '${Fit.CROP.name.lowercase()}'"
                    },
                    requireNotNull(height) {
                        "Height must be specified if fit is '${Fit.FILL.name.lowercase()}', " +
                            "'${Fit.STRETCH.name.lowercase()}', or '${Fit.CROP.name.lowercase()}'"
                    },
                )
        }
}
