package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * The IFD1 group of EXIF metadata contains thumbnail data. This data is a misprepresentation of the backing image
 * if the image has been manipulated.
 */
object RemoveThumbnailExif : VipsTransformer {
    private const val EXIF_TAG_PREFIX = "exif-ifd1"
    private const val THUMBNAIL_DATA_FIELD = "jpeg-thumbnail-data"

    override val name = "RemoveThumbnailExif"
    override val requiresAlphaState = AlphaState.EITHER

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

    /**
     * Only run this transformation if any transformations have been applied to the image.
     */
    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean = appliedTransformations.isNotEmpty()
}
