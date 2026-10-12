package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.format
import io.konifer.infrastructure.vips.interpretation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * If used in a transformation pipeline, this must go at the end. Otherwise alpha unpremultiplying will produce
 * floating point pixels.
 */
object PixelDataType : VipsTransformer {
    override val name: String = "PixelDataType"

    override fun decide(context: TransformationContext): TransformationDecision =
        if (context.transformation.format == ImageFormat.PIXELS && context.source.format() != VipsBandFormat.FORMAT_UCHAR) {
            TransformationDecision.Apply(
                // Premultiplied images will be unpremultiplied before image encoding and will result in floating point pixels
                requiredAlpha = AlphaRequirement.UN_PREMULTIPLIED,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
        } else {
            TransformationDecision.Skip
        }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val byteRangeSource =
            if (source.interpretation() == VipsInterpretation.INTERPRETATION_GREY16.rawValue) {
                // Normalize the full 16-bit range, including alpha, before casting to bytes.
                source.colourspace(VipsInterpretation.INTERPRETATION_B_W)
            } else {
                source
            }

        return VipsTransformationResult(
            processed = byteRangeSource.cast(VipsBandFormat.FORMAT_UCHAR),
            requiresLqipRegeneration = false,
        )
    }
}
