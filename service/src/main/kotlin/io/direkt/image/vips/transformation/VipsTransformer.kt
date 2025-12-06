package io.direkt.image.vips.transformation

import app.photofox.vipsffm.VImage
import io.direkt.image.model.Transformation
import io.direkt.image.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

interface VipsTransformer {
    fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult

    fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean

    val name: String
}
