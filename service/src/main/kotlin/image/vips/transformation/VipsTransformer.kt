package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import java.lang.foreign.Arena

interface VipsTransformer {
    fun transform(
        arena: Arena,
        source: VImage,
    ): VImage

    fun requiresLqipRegeneration(source: VImage): Boolean = true
}
