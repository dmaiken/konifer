package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsSize
import io.image.DimensionCalculator.calculateDimensions
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.Transformation
import io.image.vips.VipsOption.VIPS_OPTION_CROP
import io.image.vips.VipsOption.VIPS_OPTION_HEIGHT
import io.image.vips.VipsOption.VIPS_OPTION_INTERESTING
import io.image.vips.VipsOption.VIPS_OPTION_SIZE
import io.image.vips.pipeline.VipsTransformationResult
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena

/**
 * Scales the image to fit within the given width and height. [Transformation.fit] is used to define the method of fitting the
 * image into the requested height and width
 */
object Resize : VipsTransformer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override val name: String = "Resize"

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean = transformation.width != source.width || transformation.height != source.height

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val (resizeWidth, resizeHeight) =
            calculateDimensions(
                source,
                transformation.width,
                transformation.height,
                transformation.fit,
            )
        logger.info(
            "Scaling image with dimensions (${source.width}, ${source.height}) to ($resizeWidth, $resizeHeight) " +
                "using crop: ${transformation.fit}",
        )
        val regenerateLqip = requiresLqipRegeneration(source, resizeWidth, resizeHeight, transformation)
        val scaled =
            when (transformation.fit) {
                Fit.FIT ->
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Boolean(VIPS_OPTION_CROP, false),
                        VipsOption.Enum(VIPS_OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                Fit.FILL -> {
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(VIPS_OPTION_HEIGHT, resizeHeight),
                        VipsOption.Enum(VIPS_OPTION_CROP, toVipsInterestingOption(transformation.gravity)),
                        VipsOption.Enum(VIPS_OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                }
                Fit.STRETCH -> {
                    if (!transformation.canUpscale && source.width > resizeWidth && source.height > resizeHeight) {
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
                        VipsOption.Enum(VIPS_OPTION_INTERESTING, toVipsInterestingOption(transformation.gravity)),
                    )
                }
            }

        return VipsTransformationResult(
            processed = scaled,
            requiresLqipRegeneration = regenerateLqip,
        )
    }

    private fun requiresLqipRegeneration(
        source: VImage,
        resizeWidth: Int,
        resizeHeight: Int,
        transformation: Transformation,
    ): Boolean {
        if (
            (transformation.fit == Fit.STRETCH || transformation.fit == Fit.FILL) &&
            transformation.canUpscale &&
            (resizeWidth > source.width || resizeHeight > source.height)
        ) {
            return true
        }
        if (
            (transformation.fit == Fit.STRETCH || transformation.fit == Fit.FILL) &&
            (resizeWidth < source.width || resizeHeight < source.height)
        ) {
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
