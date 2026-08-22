package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

object CropFirstPage : VipsTransformer {
    override val name: String = "CropFirstPage"

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val pageHeight = source.getInt(VipsOptionNames.OPTION_PAGE_HEIGHT)

        return VipsTransformationResult(
            processed = source.extractArea(0, 0, source.width, pageHeight),
            requiresLqipRegeneration = true,
        )
    }

    override fun decide(context: TransformationContext): TransformationDecision {
        val pageCount = context.source.getInt(VipsOptionNames.OPTION_N_PAGES) ?: 1
        val pageHeight = context.source.getInt(VipsOptionNames.OPTION_PAGE_HEIGHT) ?: context.source.height
        return if (pageCount > 1 && context.source.height > pageHeight) {
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.EITHER,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
        } else {
            TransformationDecision.Skip
        }
    }
}
