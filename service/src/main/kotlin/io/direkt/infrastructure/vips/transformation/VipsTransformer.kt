package io.direkt.infrastructure.vips.transformation

import app.photofox.vipsffm.VImage
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.vips.pipeline.VipsTransformationResult
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
}
