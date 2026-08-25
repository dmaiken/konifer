package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object ForceRgbBands : VipsTransformer {
    private const val EXPECTED_BANDS = 3
    private val flattenBackground = listOf(255.0, 255.0, 255.0)
//    private val flattenBackground = listOf(0.0, 0.0, 0.0)

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val srgb = source.colourspace(VipsInterpretation.INTERPRETATION_sRGB)
        val flattened =
            if (srgb.hasAlpha()) {
                srgb.flatten(VipsOption.ArrayDouble(OPTION_BACKGROUND, flattenBackground))
            } else {
                srgb
            }

        val bands =
            flattened.getInt(OPTION_BANDS)
                ?: throw IllegalStateException("Unable to determine image band count")

        require(bands == EXPECTED_BANDS) {
            "Expected $EXPECTED_BANDS RGB bands after forcing RGB bands but found $bands"
        }

        return VipsTransformationResult(
            processed = flattened,
            requiresLqipRegeneration = false,
        )
    }

    override val name: String = "ForceRgbBands"

    override fun decide(context: TransformationContext): TransformationDecision =
        if (
            context.source.hasAlpha() ||
            context.source.getInt(OPTION_BANDS) != EXPECTED_BANDS ||
            ImageColorSpaceExtractor.extract(context.source) != ColorSpace.SRGB
        ) {
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
        } else {
            TransformationDecision.Skip
        }
}
