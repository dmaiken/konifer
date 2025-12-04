package io.image.vips.transformation

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsOperationRelational
import io.direkt.image.model.Filter
import io.direkt.image.model.Transformation
import io.direkt.image.vips.VipsOptionNames.OPTION_BANDS
import io.direkt.image.vips.VipsOptionNames.OPTION_N
import io.direkt.image.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object ColorFilter : VipsTransformer {
    /**
     * Sepia color matrix. The first line is the matrix dimensions.
     */
    val sepiaMatrix3x3 =
        """
        3 3
        0.393 0.769 0.189
        0.349 0.686 0.168
        0.272 0.534 0.131
        """.trimIndent().toByteArray()

    /**
     * Sepia color matrix for RGBA images. The first line is the matrix dimensions.
     * The last row and column allow the Alpha channel (4th band)
     * to pass through unchanged (0 0 0 1).
     */
    val sepiaMatrix4x4 =
        """
        4 4
        0.393 0.769 0.189 0.0
        0.349 0.686 0.168 0.0
        0.272 0.534 0.131 0.0
        0.0   0.0   0.0   1.0
        """.trimIndent().toByteArray()

    /**
     * Standard 3x3 Grayscale (Luminance) for RGB images.
     * Sets R, G, and B to the calculated luminance value.
     */
    val greyscaleMatrix3x3 =
        """
        3 3
        0.2126 0.7152 0.0722
        0.2126 0.7152 0.0722
        0.2126 0.7152 0.0722
        """.trimIndent().toByteArray()

    /**
     * 4x4 Grayscale for RGBA images (Preserves Transparency).
     * The last column ensures the Alpha channel is passed through (0 0 0 1).
     */
    val greyscaleMatrix4x4 =
        """
        4 4
        0.2126 0.7152 0.0722 0.0
        0.2126 0.7152 0.0722 0.0
        0.2126 0.7152 0.0722 0.0
        0.0    0.0    0.0    1.0
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
                Filter.GREYSCALE -> applyMatrix(arena, source, greyscaleMatrix3x3, greyscaleMatrix4x4)
                Filter.BLACK_WHITE -> applyBlackWhiteFilter(arena, source)
                Filter.SEPIA -> applyMatrix(arena, source, sepiaMatrix3x3, sepiaMatrix4x4)
            }

        return VipsTransformationResult(processed, requiresLqipRegeneration = transformation.filter != Filter.NONE)
    }

    private fun applyMatrix(
        arena: Arena,
        image: VImage,
        matrix3x3: ByteArray,
        matrix4x4: ByteArray,
    ): VImage {
        // Convert to scRGB to get linear light (easier for color math)
        // This preserves the alpha channel if it exists.
        val linear = image.colourspace(VipsInterpretation.INTERPRETATION_scRGB)
        val matrixBytes = if (linear.hasAlpha()) matrix4x4 else matrix3x3
        val matrixImage = VImage.matrixloadSource(arena, VSource.newFromBytes(arena, matrixBytes))

        return linear.recomb(matrixImage).colourspace(VipsInterpretation.INTERPRETATION_sRGB)
    }

    private fun applyBlackWhiteFilter(
        arena: Arena,
        source: VImage,
    ): VImage =
        if (source.hasAlpha()) {
            // Split Alpha and Color
            // Extract the last band (Alpha)
            val bands = source.getInt(OPTION_BANDS)
            val alpha = source.extractBand(bands - 1, VipsOption.Int(OPTION_N, 1))
            // Extract the color bands (RGB)
            val rgb = source.extractBand(0, VipsOption.Int(OPTION_N, bands - 1))

            // We convert RGB to B_W (grayscale) first, then threshold it
            val faxedRgb =
                rgb
                    .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                    .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
                    // Ensure it matches Alpha format (usually UChar)
                    .cast(VipsBandFormat.FORMAT_UCHAR)

            // Re-attach the original Alpha - the order of bands matters here
            VImage.bandjoin(arena, listOf(faxedRgb, alpha))
        } else {
            source
                .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                .relationalConst(VipsOperationRelational.OPERATION_RELATIONAL_MORE, blackWhiteThreshold)
        }
}
