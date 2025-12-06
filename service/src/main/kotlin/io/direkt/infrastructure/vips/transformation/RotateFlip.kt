package io.direkt.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsDirection
import io.direkt.image.model.Rotate
import io.direkt.image.model.Transformation
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_ORIENTATION
import io.direkt.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object RotateFlip : VipsTransformer {
    override val name: String = "RotateFlip"

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
                Rotate.ZERO -> 0.0
                Rotate.NINETY -> 90.0
                Rotate.ONE_HUNDRED_EIGHTY -> 180.0
                Rotate.TWO_HUNDRED_SEVENTY -> 270.0
                else -> {
                    throw IllegalStateException("Unknown rotation: ${transformation.rotate}")
                }
            }

        val processed =
            source.rotate(angle).set(OPTION_ORIENTATION, 1).let {
                if (transformation.horizontalFlip) {
                    it.flip(VipsDirection.DIRECTION_HORIZONTAL)
                } else {
                    it
                }
            }

        return VipsTransformationResult(processed, requiresLqipRegeneration = true)
    }
}
