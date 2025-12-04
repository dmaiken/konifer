package io.direkt.image.vips.pipeline

import io.direkt.image.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.image.vips.transformation.ColorFilter
import io.image.vips.transformation.GaussianBlur
import io.image.vips.transformation.Pad
import io.image.vips.transformation.Resize
import io.image.vips.transformation.RotateFlip

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
