package io.image.vips.transformation.color

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsInterpretation
import io.image.vips.transformation.VipsTransformer
import java.lang.foreign.Arena

class Greyscale : VipsTransformer {
    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        return source.colourspace(VipsInterpretation.INTERPRETATION_GREY16)
    }
}
