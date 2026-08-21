package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsAngle
import app.photofox.vipsffm.enums.VipsDirection
import io.konifer.common.image.Rotate
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ORIENTATION
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object RotateFlip : VipsTransformer {
    override val name: String = "RotateFlip"

    /**
     * Required if the transformation has any rotation or specified that is not an auto-rotation
     */
    override fun decide(context: TransformationContext): TransformationDecision {
        val transformation = context.transformation
        return if (!transformation.isAutoRotate && (transformation.rotate != Rotate.ZERO || transformation.horizontalFlip)) {
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.EITHER,
                requiredPixelAccess =
                    if (transformation.rotate == Rotate.ZERO) {
                        PixelAccess.SEQUENTIAL
                    } else {
                        PixelAccess.RANDOM
                    },
            )
        } else {
            TransformationDecision.Skip
        }
    }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val angle =
            when (transformation.rotate) {
                Rotate.ZERO -> VipsAngle.ANGLE_D0
                Rotate.NINETY -> VipsAngle.ANGLE_D90
                Rotate.ONE_HUNDRED_EIGHTY -> VipsAngle.ANGLE_D180
                Rotate.TWO_HUNDRED_SEVENTY -> VipsAngle.ANGLE_D270
                Rotate.AUTO -> throw IllegalArgumentException("Auto-rotation must be handled by ${AutoRotate.name} transformation")
            }

        val processed =
            source.rot(angle).set(OPTION_ORIENTATION, 1).let {
                if (transformation.horizontalFlip) {
                    it.flip(VipsDirection.DIRECTION_HORIZONTAL)
                } else {
                    it
                }
            }

        return VipsTransformationResult(processed, requiresLqipRegeneration = true)
    }
}
