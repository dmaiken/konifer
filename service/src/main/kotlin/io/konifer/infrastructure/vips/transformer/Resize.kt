package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsSize
import io.konifer.common.image.Fit
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.DimensionCalculator.calculateDimensions
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_CROP
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_HEIGHT
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERESTING
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_NO_ROTATE
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_SIZE
import io.konifer.infrastructure.vips.pageSafeHeight
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import io.konifer.infrastructure.vips.toVipsInteresting
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import java.lang.foreign.Arena
import kotlin.math.min

/**
 * Scales the image to fit within the given width and height. [Transformation.fit] is used to define the method of fitting the
 * image into the requested height and width
 */
object Resize : VipsTransformer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override val name: String = "Resize"
    override val requiresAlphaState: AlphaState = AlphaState.UN_PREMULTIPLIED

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean = createPlan(source, transformation).requiresTransformation

    fun createPlan(
        source: VImage,
        transformation: Transformation,
    ): ResizePlan {
        val sourceWidth = source.width
        val sourceHeight = source.pageSafeHeight()
        val (calculatedWidth, calculatedHeight) =
            calculateDimensions(
                source,
                transformation.width,
                transformation.height,
                transformation.fit,
            )

        val (targetWidth, targetHeight) =
            when {
                transformation.canUpscale -> Pair(calculatedWidth, calculatedHeight)
                transformation.fit == Fit.STRETCH ->
                    Pair(
                        min(calculatedWidth, sourceWidth),
                        min(calculatedHeight, sourceHeight),
                    )
                transformation.fit == Fit.FIT &&
                    (calculatedWidth > sourceWidth || calculatedHeight > sourceHeight) ->
                    Pair(sourceWidth, sourceHeight)
                else -> Pair(calculatedWidth, calculatedHeight)
            }

        val requiresTransformation =
            when (transformation.fit) {
                Fit.FILL ->
                    if (transformation.canUpscale) {
                        targetWidth != sourceWidth || targetHeight != sourceHeight
                    } else {
                        targetWidth < sourceWidth || targetHeight < sourceHeight
                    }
                Fit.FIT, Fit.STRETCH, Fit.CROP ->
                    targetWidth != sourceWidth || targetHeight != sourceHeight
            }

        return ResizePlan(
            width = targetWidth,
            height = targetHeight,
            requiresTransformation = requiresTransformation,
            requiresLqipRegeneration =
                requiresTransformation &&
                    (transformation.fit == Fit.STRETCH || transformation.fit == Fit.FILL),
        )
    }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val plan = createPlan(source, transformation)
        logger.debug {
            "Scaling image with dimensions (${source.width}, ${source.pageSafeHeight()}) to (${plan.width}, ${plan.height}) " +
                "using fit: ${transformation.fit}"
        }
        val scaled =
            when (transformation.fit) {
                Fit.FIT ->
                    source.thumbnailImage(
                        plan.width,
                        VipsOption.Int(OPTION_HEIGHT, plan.height),
                        VipsOption.Boolean(OPTION_CROP, false),
                        VipsOption.Boolean(OPTION_NO_ROTATE, true),
                        VipsOption.Enum(OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                Fit.FILL -> {
                    source.thumbnailImage(
                        plan.width,
                        VipsOption.Int(OPTION_HEIGHT, plan.height),
                        VipsOption.Enum(OPTION_CROP, transformation.gravity.toVipsInteresting()),
                        VipsOption.Boolean(OPTION_NO_ROTATE, true),
                        VipsOption.Enum(OPTION_SIZE, if (transformation.canUpscale) VipsSize.SIZE_BOTH else VipsSize.SIZE_DOWN),
                    )
                }
                Fit.STRETCH ->
                    source.thumbnailImage(
                        plan.width,
                        VipsOption.Int(OPTION_HEIGHT, plan.height),
                        VipsOption.Boolean(OPTION_CROP, false),
                        VipsOption.Boolean(OPTION_NO_ROTATE, true),
                        VipsOption.Enum(OPTION_SIZE, VipsSize.SIZE_FORCE),
                    )
                Fit.CROP -> {
                    source.smartcrop(
                        plan.width,
                        plan.height,
                        VipsOption.Enum(OPTION_INTERESTING, transformation.gravity.toVipsInteresting()),
                    )
                }
            }

        return VipsTransformationResult(
            processed = scaled,
            requiresLqipRegeneration = plan.requiresLqipRegeneration,
        )
    }
}

data class ResizePlan(
    val width: Int,
    val height: Int,
    val requiresTransformation: Boolean,
    val requiresLqipRegeneration: Boolean,
)
