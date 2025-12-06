package io.direkt.image.vips.pipeline

import io.direkt.image.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.direkt.image.vips.transformation.ColorFilter
import io.direkt.image.vips.transformation.GaussianBlur
import io.direkt.image.vips.transformation.Pad
import io.direkt.image.vips.transformation.Resize
import io.direkt.image.vips.transformation.RotateFlip

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
