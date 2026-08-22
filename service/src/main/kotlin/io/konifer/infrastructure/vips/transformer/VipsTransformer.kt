package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

interface VipsTransformer {
    val name: String

    /**
     * Decides whether this transformer should run and, when it should, which image state it requires.
     * Implementations should only inspect the request and image header; pixel evaluation belongs in [transform].
     */
    fun decide(context: TransformationContext): TransformationDecision

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
}

data class TransformationContext(
    val arena: Arena,
    val source: VImage,
    val transformation: Transformation,
    val appliedTransformations: List<AppliedTransformation>,
)
