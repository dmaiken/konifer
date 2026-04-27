package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
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

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean {
        val nPages = source.getInt(VipsOptionNames.OPTION_N_PAGES) ?: 1
        if (nPages == 1) {
            return false
        }

        val pageHeight = source.getInt(VipsOptionNames.OPTION_PAGE_HEIGHT) ?: source.height
        return source.height > pageHeight
    }

    override val requiresAlphaState: AlphaState = AlphaState.UN_PREMULTIPLIED
}
