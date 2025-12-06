package io.direkt.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInteresting
import app.photofox.vipsffm.enums.VipsSize
import io.direkt.infrastructure.vips.DimensionCalculator.calculateDimensions
import io.direkt.image.model.Fit
import io.direkt.image.model.Gravity
import io.direkt.image.model.Transformation
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_CROP
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_HEIGHT
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_INTERESTING
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_SIZE
import io.direkt.infrastructure.vips.pipeline.VipsTransformationResult
import io.image.vips.pageSafeHeight
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
            "Scaling image with dimensions (${source.width}, ${source.pageSafeHeight()}) to ($resizeWidth, $resizeHeight) " +
                "using fit: ${transformation.fit}",
        )
        val regenerateLqip = requiresLqipRegeneration(source, resizeWidth, resizeHeight, transformation)
        val scaled =
            when (transformation.fit) {
                Fit.FIT ->
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(OPTION_HEIGHT, resizeHeight),
                        VipsOption.Boolean(OPTION_CROP, false),
                        VipsOption.Enum(OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                Fit.FILL -> {
                    source.thumbnailImage(
                        resizeWidth,
                        VipsOption.Int(OPTION_HEIGHT, resizeHeight),
                        VipsOption.Enum(OPTION_CROP, toVipsInterestingOption(transformation.gravity)),
                        VipsOption.Enum(OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
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
                            VipsOption.Int(OPTION_HEIGHT, resizeHeight),
                            VipsOption.Boolean(OPTION_CROP, false),
                            VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_FORCE),
                        )
                    }
                }
                Fit.CROP -> {
                    source.smartcrop(
                        resizeWidth,
                        resizeHeight,
                        VipsOption.Enum(OPTION_INTERESTING, toVipsInterestingOption(transformation.gravity)),
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
