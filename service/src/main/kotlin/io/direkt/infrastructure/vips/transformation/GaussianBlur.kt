package io.direkt.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import io.direkt.image.model.Transformation
import io.direkt.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * Apply a Gaussian blur to the source. LQIPs will never need to be regenerated since they're a blurring of the
 * image already. Additionally, the color and spatial structure of the image is not altered.
 */
object GaussianBlur : VipsTransformer {
    override val name: String = "GaussianBlur"

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean = transformation.blur > 0

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult =
        VipsTransformationResult(
            processed = source.gaussblur(transformation.blur / 2.0),
            requiresLqipRegeneration = false,
        )
}
