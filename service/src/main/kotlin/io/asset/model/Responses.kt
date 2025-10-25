package io.asset.model

import io.serializers.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class AssetResponse(
    val `class`: AssetClass,
    val alt: String?,
    val entryId: Long,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val variants: List<AssetVariantResponse>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
)

@Serializable
data class AssetVariantResponse(
    val bucket: String,
    val storeKey: String,
    val imageAttributes: ImageAttributeResponse,
    val lqip: LQIPResponse,
)

@Serializable
data class ImageAttributeResponse(
    val height: Int,
    val width: Int,
    val mimeType: String,
)

@Serializable
data class LQIPResponse(
    val blurhash: String?,
    val thumbhash: String?,
)

@Serializable
data class AssetLinkResponse(
    val url: String,
    val lqip: LQIPResponse,
)

enum class AssetClass {
    IMAGE,
}
