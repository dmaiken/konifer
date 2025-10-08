package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsSize
import io.image.DimensionCalculator.calculateDimensions
import io.image.model.Fit
import io.image.model.Gravity
import io.image.vips.VipsOption.VIPS_OPTION_CROP
import io.image.vips.VipsOption.VIPS_OPTION_HEIGHT
import io.image.vips.VipsOption.VIPS_OPTION_INTERESTING
import io.image.vips.VipsOption.VIPS_OPTION_SIZE
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena

class Resize(
    private val width: Int?,
    private val height: Int?,
    private val fit: Fit,
    private val upscale: Boolean,
    private val gravity: Gravity,
) : VipsTransformer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    /**
     * Scales the image to fit within the given width and height. [fit] is used to define the method of firring the
     * image into the requested height and width
     */
    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        if (width == null && height == null) {
            logger.info("width and height are not set, skipping resize transformation")
            return source
        }

        val (resizeWidth, resizeHeight) = calculateDimensions(source, width, height, fit)
        logger.info("Scaling image with dimensions (${source.width}, ${source.height}) to ($resizeWidth, $resizeHeight) using crop: $fit")
        val scaled =
            when (fit) {
                Fit.FIT ->
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Boolean(VIPS_OPTION_CROP, false),
                        VipsOption.Enum(VIPS_OPTION_SIZE, if (upscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                Fit.FILL -> {
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Enum(VIPS_OPTION_CROP, toVipsInterestingOption(gravity)),
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
                Fit.CROP -> {
                    source.smartcrop(
                        resizeWidth,
                        resizeHeight,
                        VipsOption.Enum(VIPS_OPTION_INTERESTING, toVipsInterestingOption(gravity)),
                    )
                }
            }
        return scaled
    }

    override fun requiresLqipRegeneration(source: VImage): Boolean {
        val (resizeWidth, resizeHeight) = calculateDimensions(source, width, height, fit)
        if ((fit == Fit.STRETCH || fit == Fit.FILL) && upscale && (resizeWidth > source.width || resizeHeight > source.height)) {
            return true
        }
        if ((fit == Fit.STRETCH || fit == Fit.FILL) && (resizeWidth < source.width || resizeHeight < source.height)) {
            return true
        }

        return false
    }

    private fun toVipsInterestingOption(gravity: Gravity): VipsInteresting =
        when (gravity) {
            Gravity.CENTER -> VipsInteresting.INTERESTING_CENTRE
            Gravity.ATTENTION -> VipsInteresting.INTERESTING_ATTENTION
            Gravity.ENTROPY -> VipsInteresting.INTERESTING_ENTROPY
        }
}
