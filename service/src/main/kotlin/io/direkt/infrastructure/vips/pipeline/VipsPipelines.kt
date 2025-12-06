package io.direkt.infrastructure.vips.pipeline

import io.direkt.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.direkt.infrastructure.vips.transformation.ColorFilter
import io.direkt.infrastructure.vips.transformation.GaussianBlur
import io.direkt.infrastructure.vips.transformation.Pad
import io.direkt.infrastructure.vips.transformation.Resize
import io.direkt.infrastructure.vips.transformation.RotateFlip

object VipsPipelines {
    val lqipVariantPipeline =
        vipsPipeline {
            add(Resize)
        }.build()

    val preProcessingPipeline =
        vipsPipeline {
            add(Resize)
            add(RotateFlip)
            add(ColorFilter)
            add(GaussianBlur)
            add(Pad)
        }.build()

    /**
     * Currently the same as [preProcessingPipeline]
     */
    val variantGenerationPipeline = preProcessingPipeline
}
