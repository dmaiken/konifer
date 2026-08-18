package io.konifer.infrastructure.vips.pipeline

import io.konifer.infrastructure.vips.pipeline.VipsPipelines.preProcessingPipeline
import io.konifer.infrastructure.vips.transformer.ColorFilter
import io.konifer.infrastructure.vips.transformer.CropFirstPage
import io.konifer.infrastructure.vips.transformer.ForceRgbBands
import io.konifer.infrastructure.vips.transformer.GaussianBlur
import io.konifer.infrastructure.vips.transformer.Pad
import io.konifer.infrastructure.vips.transformer.Resize
import io.konifer.infrastructure.vips.transformer.RotateFlip
import io.konifer.infrastructure.vips.transformer.StripMetadata
import io.konifer.infrastructure.vips.transformer.StripThumbnailExif
import io.konifer.infrastructure.vips.transformer.TransformColorSpace

object VipsPipelines {
    val lqipVariantPipeline =
        vipsPipeline {
            add(CropFirstPage)
            add(Resize)
        }.build()

    val preProcessingPipeline =
        vipsPipeline {
            // Spatial transformations
            add(RotateFlip)
            add(Resize)

            // Color standardization
            add(TransformColorSpace)

            // Color-sensitive manipulations
            add(ColorFilter)
            add(GaussianBlur)
            add(Pad)
            add(TransformColorSpace)
            add(StripMetadata)
            add(StripThumbnailExif)
        }.build()

    val tensorProcessingPipeline =
        vipsPipeline {
            add(CropFirstPage)
            add(RotateFlip)
            add(Resize)
            add(TransformColorSpace)
            add(ForceRgbBands)
        }.build()

    /**
     * Currently the same as [preProcessingPipeline]
     */
    val variantGenerationPipeline = preProcessingPipeline
}
