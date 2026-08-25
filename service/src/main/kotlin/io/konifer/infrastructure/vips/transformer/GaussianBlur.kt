package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * Apply a Gaussian blur to the source. LQIPs will never need to be regenerated since they're a blurring of the
 * image already. Additionally, the color and spatial structure of the image is not altered.
 */
object GaussianBlur : VipsTransformer {
    override val name: String = "GaussianBlur"

    override fun decide(context: TransformationContext): TransformationDecision =
        if (context.transformation.blur.value > 0) {
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.PREMULTIPLIED,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
        } else {
            TransformationDecision.Skip
        }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult =
        VipsTransformationResult(
            processed = source.gaussblur(transformation.blur.value / 2.0),
            requiresLqipRegeneration = false,
        )
}
