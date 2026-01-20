package io.konifer.infrastructure.vips.pipeline

import io.konifer.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.konifer.infrastructure.vips.transformation.ColorFilter
import io.konifer.infrastructure.vips.transformation.CropFirstPage
import io.konifer.infrastructure.vips.transformation.GaussianBlur
import io.konifer.infrastructure.vips.transformation.Pad
import io.konifer.infrastructure.vips.transformation.Resize
import io.konifer.infrastructure.vips.transformation.RotateFlip

object VipsPipelines {
    val lqipVariantPipeline =
        vipsPipeline {
            add(CropFirstPage)
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
