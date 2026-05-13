package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsIntent
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BLACK_POINT_COMPENSATION
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTENT
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import io.konifer.infrastructure.vips.transformer.TransformColorSpace.ProfileNames.DISPLAY_P3
import io.konifer.infrastructure.vips.transformer.TransformColorSpace.ProfileNames.SRGB
import java.lang.foreign.Arena

object TransformColorSpace : VipsTransformer {
    object ProfileNames {
        const val SRGB = "srgb"
        const val DISPLAY_P3 = "p3"
    }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val profileName =
            when (transformation.colorSpace) {
                ColorSpace.P3 -> DISPLAY_P3
                ColorSpace.SRGB -> SRGB
                else -> throw IllegalArgumentException(
                    "Invalid ICC profile. Transformation to ${transformation.colorSpace.name} is not supported.",
                )
            }
        val transformed =
            source.iccTransform(
                profileName,
                VipsOption.Boolean(OPTION_BLACK_POINT_COMPENSATION, true),
                VipsOption.Enum(OPTION_INTENT, VipsIntent.INTENT_RELATIVE),
            )

        return VipsTransformationResult(
            processed = transformed,
            requiresLqipRegeneration = true,
        )
    }

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean {
        val currentColorSpace = ImageColorSpaceExtractor.extract(source)

        return currentColorSpace != transformation.colorSpace
    }

    override val requiresAlphaState = AlphaState.UN_PREMULTIPLIED
    override val name = "TransformColorSpace"
}
