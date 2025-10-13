package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.image.model.Filter
import io.image.model.Transformation
import io.image.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object ColorFilter : VipsTransformer {
    /**
     * Sepia color matrix. The first line is the matrix dimensions.
     */
    val sepiaMatrix =
        """
        3 3
        0.393 0.769 0.189
        0.349 0.686 0.168
        0.272 0.534 0.131
        """.trimIndent().toByteArray()

    val blackWhiteThreshold = listOf(128.0)

    override val name: String = "ColorFilter"

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): Boolean = transformation.filter != Filter.NONE

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val processed =
            when (transformation.filter) {
                Filter.NONE -> source
                Filter.GREYSCALE -> source.colourspace(VipsInterpretation.INTERPRETATION_GREY16)
                Filter.BLACK_WHITE ->
                    source
                        .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                        .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                Filter.SEPIA -> {
                    val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, sepiaMatrix))

                    source
                        .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
                        .recomb(matrixImage)
                        .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
                }
            }

        return VipsTransformationResult(processed, requiresLqipRegeneration = transformation.filter != Filter.NONE)
    }
}
