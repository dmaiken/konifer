package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.common.image.MetadataType
import io.konifer.domain.variant.MetadataTransformation
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.konifer.infrastructure.vips.pipeline.VipsTransformationResult
import java.lang.foreign.Arena

/**
 * Strip EXIF, XPM, and/or IPTC metadata if configured to do so
 */
object StripMetadata : VipsTransformer {
    private const val EXIF_TAG_PREFIX = "exif-"
    private const val XMP_TAG = "xmp-data"
    private const val IPTC_TAG = "iptc-data"
    private const val IFD1_TAG_NAME = "jpeg-thumbnail-data"

    override val requiresAlphaState = AlphaState.EITHER

    override val name = "StripMetadata"

    override fun transform(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
    ): VipsTransformationResult {
        source.fields
            .filter { shouldStrip(it, transformation.metadata) }
            .forEach { source.remove(it) }

        return VipsTransformationResult(
            processed = source,
            requiresLqipRegeneration = false,
        )
    }

    override fun requiresTransformation(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        appliedTransformations: List<AppliedTransformation>,
    ): Boolean =
        source.fields
            .any { shouldStrip(it, transformation.metadata) }

    private fun shouldStrip(
        field: String,
        metadata: MetadataTransformation,
    ): Boolean =
        when {
            metadata.strip.contains(MetadataType.EXIF) && (field.startsWith(EXIF_TAG_PREFIX) || field == IFD1_TAG_NAME) -> true
            metadata.strip.contains(MetadataType.XMP) && field == XMP_TAG -> true
            metadata.strip.contains(MetadataType.IPTC) && field == IPTC_TAG -> true
            else -> false
        }
}
