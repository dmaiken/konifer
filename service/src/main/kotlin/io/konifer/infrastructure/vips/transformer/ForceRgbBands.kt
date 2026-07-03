package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
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

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean =
        source.hasAlpha() ||
            source.getInt(OPTION_BANDS) != EXPECTED_BANDS ||
            ImageColorSpaceExtractor.extract(source) != ColorSpace.SRGB

    override val requiresAlphaState: AlphaState = AlphaState.UN_PREMULTIPLIED
    override val name: String = "ForceRgbBands"
}
