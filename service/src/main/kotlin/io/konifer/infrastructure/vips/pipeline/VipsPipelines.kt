package io.konifer.infrastructure.vips.pipeline

import io.konifer.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.konifer.infrastructure.vips.transformer.ColorFilter
import io.konifer.infrastructure.vips.transformer.CropFirstPage
import io.konifer.infrastructure.vips.transformer.GaussianBlur
import io.konifer.infrastructure.vips.transformer.Pad
import io.konifer.infrastructure.vips.transformer.RemoveThumbnailExif
import io.konifer.infrastructure.vips.transformer.Resize
import io.konifer.infrastructure.vips.transformer.RotateFlip

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
            add(RemoveThumbnailExif)
        }.build()

    /**
     * Currently the same as [preProcessingPipeline]
     */
    val variantGenerationPipeline = preProcessingPipeline
}
