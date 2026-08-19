package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.common.image.Rotate
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object AutoRotate : VipsTransformer {
    override val name: String = "AutoRotate"
    override val requiresAlphaState: AlphaState = AlphaState.EITHER

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult =
        VipsTransformationResult(
            processed = source.autorot(),
            requiresLqipRegeneration = changesImageOrientation(transformation),
        )

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean = transformation.isAutoRotate

    /**
     * Auto-rotation always normalizes orientation metadata, but only invalidates image-derived
     * content when the resolved EXIF orientation moves pixels.
     */
    fun changesImageOrientation(transformation: Transformation): Boolean =
        transformation.isAutoRotate &&
            (transformation.rotate != Rotate.ZERO || transformation.horizontalFlip)
}
