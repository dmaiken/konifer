package io.image.vips.transformation

import app.photofox.vipsffm.VImage

interface VipsTransformer {
    fun transform(source: VImage): VImage

    fun requiresLqipRegeneration(source: VImage): Boolean = true
}
