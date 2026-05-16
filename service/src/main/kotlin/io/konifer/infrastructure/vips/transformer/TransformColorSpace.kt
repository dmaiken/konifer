package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsIntent
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BLACK_POINT_COMPENSATION
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTENT
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import io.konifer.infrastructure.vips.transformer.TransformColorSpace.ProfileNames.DISPLAY_P3
import io.konifer.infrastructure.vips.transformer.TransformColorSpace.ProfileNames.GRAYSCALE
import io.konifer.infrastructure.vips.transformer.TransformColorSpace.ProfileNames.SRGB
import java.lang.foreign.Arena

object TransformColorSpace : VipsTransformer {
    object ProfileNames {
        const val SRGB = "srgb"
        const val DISPLAY_P3 = "p3"
        const val GRAYSCALE = "bw"
    }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        val transformed =
            if (transformation.colorSpace == ColorSpace.Grayscale) {
                source.colourspace(VipsInterpretation.INTERPRETATION_B_W).apply {
                    // Strip the ICC profile so the image doesn't lie
                    remove("icc-profile-data")
                }
            } else {
                val profileName =
                    when (transformation.colorSpace) {
                        ColorSpace.P3 -> DISPLAY_P3
                        ColorSpace.SRGB -> SRGB
                        ColorSpace.Grayscale -> GRAYSCALE
                        else -> throw IllegalArgumentException(
                            "Invalid ICC profile. Transformation to ${transformation.colorSpace.name} is not supported.",
                        )
                    }
                source.iccTransform(
                    profileName,
                    VipsOption.Boolean(OPTION_BLACK_POINT_COMPENSATION, true),
                    VipsOption.Enum(OPTION_INTENT, VipsIntent.INTENT_RELATIVE),
                )
            }

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
