package io.konifer.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsAngle
import app.photofox.vipsffm.enums.VipsDirection
import io.konifer.domain.image.Rotate
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ORIENTATION
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object RotateFlip : VipsTransformer {
    override val name: String = "RotateFlip"
    override val requiresAlphaState: AlphaState = AlphaState.PREMULTIPLIED

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean = transformation.rotate != Rotate.ZERO || transformation.horizontalFlip

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        if (transformation.rotate == Rotate.AUTO) {
            // Vips autorotate strips the exif metadata tag
            return VipsTransformationResult(
                processed = source.autorot(),
                requiresLqipRegeneration = true,
            )
        }

        val angle =
            when (transformation.rotate) {
                Rotate.ZERO -> VipsAngle.ANGLE_D0
                Rotate.NINETY -> VipsAngle.ANGLE_D90
                Rotate.ONE_HUNDRED_EIGHTY -> VipsAngle.ANGLE_D180
                Rotate.TWO_HUNDRED_SEVENTY -> VipsAngle.ANGLE_D270
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
