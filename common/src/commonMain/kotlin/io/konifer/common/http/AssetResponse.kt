package io.konifer.common.http

import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.SortedMetadataTypeSerializer
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AssetResponse(
    val `class`: AssetClass,
    val alt: String?,
    val entryId: Long,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val source: AssetSource,
    val externalSourceAddress: String?,
    @Deprecated("use externalSourceReference")
    val sourceUrl: String?,
    val variants: List<VariantResponse>,
    val createdAt: Instant,
    val modifiedAt: Instant,
) {
    companion object Factory
}

@Serializable
data class VariantResponse(
    val isOriginalVariant: Boolean,
    val storeBucket: String,
    val storeKey: String,
    val attributes: AttributesResponse,
    val transformation: TransformationResponse?,
    val lqip: LQIPResponse,
) {
    companion object Factory
}

@Serializable
data class AttributesResponse(
    val height: Int,
    val width: Int,
    val format: String,
    val colorSpace: String,
    val pageCount: Int?,
    val loop: Int?,
    val pixels: PixelAttributesResponse?,
    /** Clockwise rotation still needed for display, followed by [horizontalFlip]. Never auto. */
    val rotate: Rotate,
    val horizontalFlip: Boolean,
) {
    companion object Factory
}

@Serializable
data class PixelAttributesResponse(
    val channels: String,
    val layout: PixelLayout,
    val dataType: PixelDataType,
)

@Serializable
data class TransformationResponse(
    val width: Int,
    val height: Int,
    val fit: Fit,
    val gravity: Gravity,
    val format: String,
    val rotate: Rotate,
    val flip: Flip,
    val filter: Filter,
    val blur: Int,
    val quality: Int,
    val padding: PaddingResponse,
    val metadata: MetadataResponse,
    val colorSpace: String,
) {
    companion object Factory
}

@Serializable
data class LQIPResponse(
    val blurhash: String?,
    val thumbhash: String?,
) {
    companion object Factory
}

@Serializable
data class AssetLinkResponse(
    val url: String,
    val expiresAt: Instant?,
    val alt: String?,
    val lqip: LQIPResponse,
    val attributes: AttributesResponse,
)

@Serializable
data class PaddingResponse(
    val amount: Int,
    val color: List<Int>,
)

@Serializable
data class MetadataResponse(
    @Serializable(with = SortedMetadataTypeSerializer::class)
    val strip: List<MetadataType>,
)
