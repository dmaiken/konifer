package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsSize
import io.image.model.Fit
import io.image.vips.VipsOption.VIPS_OPTION_CROP
import io.image.vips.VipsOption.VIPS_OPTION_HEIGHT
import io.image.vips.VipsOption.VIPS_OPTION_SIZE
import io.ktor.util.logging.KtorSimpleLogger

class Resize(
    private val width: Int?,
    private val height: Int?,
    private val fit: Fit,
    private val upscale: Boolean,
) : VipsTransformer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Scales the image to fit within the given width and height. [fit] is used to define the method of firring the
     * image into the requested height and width
     */
    override fun transform(source: VImage): VImage {
        if (width == null && height == null) {
            logger.info("Preprocessing width and height are not set, skipping preprocessing downscaling")
            return source
        }

        val (resizeWidth, resizeHeight) = calculateDimensions(source, width, height, fit)
        logger.info("Scaling image with dimensions ($width, $height) to ($resizeWidth, $resizeHeight) using crop: $fit")

        val scaled =
            when (fit) {
                Fit.SCALE ->
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Enum(VIPS_OPTION_SIZE, if (upscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                Fit.FIT -> {
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Boolean(VIPS_OPTION_CROP, true),
                        VipsOption.Enum(VIPS_OPTION_SIZE, if (upscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                }
                Fit.STRETCH -> {
                    if (!upscale && source.width > resizeWidth && source.height > resizeHeight) {
                        // [VipsSize] does not have "force fit but also only downscale"
                        // In this case, don't do anything since the resize dimensions are larger than the image itself
                        source
                    } else {
                        source.thumbnailImage(
                            resizeWidth,
                            VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                            VipsOption.Boolean(VIPS_OPTION_CROP, false),
                            VipsOption.Enum(VIPS_OPTION_SIZE, VipsSize.SIZE_FORCE),
                        )
                    }
                }
            }
        return scaled
    }

    override fun requiresLqipRegeneration(source: VImage): Boolean {
        val (resizeWidth, resizeHeight) = calculateDimensions(source, width, height, fit)
        if ((fit == Fit.STRETCH || fit == Fit.FIT) && upscale && (resizeWidth > source.width || resizeHeight > source.height)) {
            return true
        }
        if ((fit == Fit.STRETCH || fit == Fit.FIT) && (resizeWidth < source.width || resizeHeight < source.height)) {
            return true
        }

        return false
    }

    private fun calculateDimensions(
        image: VImage,
        width: Int?,
        height: Int?,
        fit: Fit,
    ): Pair<Int, Int> =
        when (fit) {
            Fit.SCALE -> {
                val widthRatio =
                    width?.let {
                        it.toDouble() / image.width
                    } ?: 1.0
                val heightRatio =
                    height?.let {
                        it.toDouble() / image.height
                    } ?: 1.0
                Pair((image.width * widthRatio).toInt(), (image.height * heightRatio).toInt())
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
