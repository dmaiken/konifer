package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

interface VipsTransformer {
    /**
     * Add the transformation to the Vips transformation pipeline. Note that vips transformation is inherently
     * demand-driven and the actual transformation will not apply unless the image is written to an output
     * file, sink, target, stream, etc. The exception to this are transformations to image metadata such as
     * EXIF.
     */
    fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult

    /**
     * Whether the image requires [transform] to be called on it. If false is returned, then the image does not
     * need the implementing transformer applied to it.
     */
    fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean

    /**
     * What state does alpha need to be in before running this transformer. Implementations should
     * assume that the image passed to [transform] will have alpha in the desired state.
     */
    val requiresAlphaState: AlphaState

    val name: String
}

enum class AlphaState {
    PREMULTIPLIED,
    UN_PREMULTIPLIED,
    EITHER,
}
