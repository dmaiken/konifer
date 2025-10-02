package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsDirection
import io.image.model.Rotate
import io.ktor.util.logging.KtorSimpleLogger
import java.lang.foreign.Arena

class RotateFlip(
    /**
     * Clockwise rotation
     */
    private val rotate: Rotate,
    /**
     * Ignored if [rotate] is [Rotate.AUTO]
     */
    private val horizontalFlip: Boolean,
) : VipsTransformer {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        if (rotate == Rotate.ZERO && !horizontalFlip) {
            logger.info("Rotation is: $rotate and no flipping requested, skipping RotateFlip transformation")
            return source
        }

        if (rotate == Rotate.AUTO) {
            // Vips autorotate strips the exif metadata tag
            return source.autorot()
        }

        val angle =
            when (rotate) {
                Rotate.ZERO -> 0.0
                Rotate.NINETY -> 90.0
                Rotate.ONE_HUNDRED_EIGHTY -> 180.0
                Rotate.TWO_HUNDRED_SEVENTY -> 270.0
                else -> {
                    throw IllegalStateException("Unknown rotation: $rotate")
                }
            }

        return source.rotate(angle).set("orientation", 1).let {
            if (horizontalFlip) {
                it.flip(VipsDirection.DIRECTION_HORIZONTAL)
            } else {
                it
            }
        }
    }
}
