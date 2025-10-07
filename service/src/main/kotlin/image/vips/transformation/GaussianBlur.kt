package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import java.lang.foreign.Arena

/**
 * Apply a Gaussian blur to the source. LQIPs will never need to be regenerated since they're a blurring of the
 * image already. Additionally, the color and spatial structure of the image is not altered.
 */
class GaussianBlur(
    val blurAmount: Int,
) : VipsTransformer {
    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        if (blurAmount == 0) {
            return source
        }

        return source.gaussblur(blurAmount / 2.0)
    }

    override fun requiresLqipRegeneration(source: VImage): Boolean = false
}
