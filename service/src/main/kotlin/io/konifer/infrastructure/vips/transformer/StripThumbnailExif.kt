package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * The IFD1 group of EXIF metadata contains thumbnail data. This data is a misprepresentation of the backing image
 * if the image has been manipulated.
 */
object StripThumbnailExif : VipsTransformer {
    private const val EXIF_TAG_PREFIX = "exif-ifd1"
    private const val THUMBNAIL_DATA_FIELD = "jpeg-thumbnail-data"

    override val name = "RemoveThumbnailExif"

    override fun decide(context: TransformationContext): TransformationDecision =
        if (
            context.appliedTransformations.isNotEmpty() &&
            !(context.appliedTransformations.size == 1 && context.appliedTransformations.first().name == StripMetadata.name)
        ) {
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.EITHER,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
        } else {
            TransformationDecision.Skip
        }

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        source.fields
            .filter { it.startsWith(EXIF_TAG_PREFIX) }
            .forEach { source.remove(it) }

        // What libvips actually uses to encode against
        source.remove(THUMBNAIL_DATA_FIELD)

        return VipsTransformationResult(
            processed = source,
            requiresLqipRegeneration = false,
        )
    }
}
