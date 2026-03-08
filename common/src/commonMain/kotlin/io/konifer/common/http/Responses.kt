package io.konifer.common.http

import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.Rotate
import io.konifer.common.serializer.AssetClassSerializer
import io.konifer.common.serializer.AssetSourceSerializer
import io.konifer.common.serializer.FilterSerializer
import io.konifer.common.serializer.FitSerializer
import io.konifer.common.serializer.FlipSerializer
import io.konifer.common.serializer.GravitySerializer
import io.konifer.common.serializer.LocalDateTimeSerializer
import io.konifer.common.serializer.RotateSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class AssetResponse(
    @Serializable(with = AssetClassSerializer::class)
    val `class`: AssetClass,
    val alt: String?,
    val entryId: Long,
    val labels: Map<String, String>,
    val tags: Set<String>,
    @Serializable(with = AssetSourceSerializer::class)
    val source: AssetSource,
    val sourceUrl: String?,
    val variants: List<VariantResponse>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val modifiedAt: LocalDateTime,
) {
    companion object Factory
}

@Serializable
data class VariantResponse(
    val isOriginalVariant: Boolean,
    val storeBucket: String,
    val storeKey: String,
    val attributes: AttributeResponse,
    val transformation: TransformationResponse?,
    val lqip: LQIPResponse,
) {
    companion object Factory
}

@Serializable
data class AttributeResponse(
    val height: Int,
    val width: Int,
    val format: String,
    val pageCount: Int?,
    val loop: Int?,
) {
    companion object Factory
}

@Serializable
data class TransformationResponse(
    val width: Int,
    val height: Int,
    @Serializable(with = FitSerializer::class)
    val fit: Fit,
    @Serializable(with = GravitySerializer::class)
    val gravity: Gravity,
    val format: String,
    @Serializable(with = RotateSerializer::class)
    val rotate: Rotate,
    @Serializable(with = FlipSerializer::class)
    val flip: Flip,
    @Serializable(with = FilterSerializer::class)
    val filter: Filter,
    val blur: Int,
    val quality: Int,
    val padding: PaddingResponse,
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
    val alt: String?,
    val lqip: LQIPResponse,
)

@Serializable
data class PaddingResponse(
    val amount: Int,
    val color: List<Int>,
)
