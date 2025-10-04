package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.image.model.Filter
import java.lang.foreign.Arena

class ColorFilter(private val filter: Filter) : VipsTransformer {
    companion object {
        val sepiaText =
            """
            3 3
            0.393 0.769 0.189
            0.349 0.686 0.168
            0.272 0.534 0.131
            """.trimIndent().toByteArray()

        val blackWhiteThreshold = listOf(128.0)
    }

    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        return when (filter) {
            Filter.NONE -> source
            Filter.GREYSCALE -> source.colourspace(VipsInterpretation.INTERPRETATION_GREY16)
            Filter.BLACK_WHITE ->
                source
                    .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                    .colourspace(VipsInterpretation.INTERPRETATION_B_W)
            Filter.SEPIA -> {
                val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaText))

                return source
                    .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                    .recomb(matrixImage)
                    .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
            }
        }
    }
}
