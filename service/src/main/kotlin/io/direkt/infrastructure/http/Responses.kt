package io.direkt.infrastructure.http

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetClass
import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetSource
import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantData
import io.direkt.infrastructure.http.serialization.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class AssetResponse(
    val `class`: AssetClass,
    val alt: String?,
    val entryId: Long,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val source: AssetSource,
    val sourceUrl: String?,
    val variants: List<VariantResponse>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val modifiedAt: LocalDateTime,
) {
    companion object Factory {
        fun fromAssetData(assetData: AssetData): AssetResponse = AssetResponse(
            `class` = AssetClass.IMAGE,
            alt = assetData.alt,
            entryId = assetData.entryId,
            labels = assetData.labels,
            tags = assetData.tags,
            source = assetData.source,
            sourceUrl = assetData.sourceUrl,
            variants = assetData.variants.map { VariantResponse.fromVariantData(it) },
            createdAt = assetData.createdAt,
            modifiedAt = assetData.modifiedAt,
        )

        fun fromAsset(asset: Asset): AssetResponse = AssetResponse(
            `class` = AssetClass.IMAGE,
            alt = asset.alt,
            entryId = checkNotNull(asset.entryId),
            labels = asset.labels,
            tags = asset.tags,
            source = asset.source,
            sourceUrl = asset.sourceUrl,
            variants = asset.variants.map { VariantResponse.fromVariant(it) },
            createdAt = asset.createdAt,
            modifiedAt = asset.modifiedAt,
        )
    }
}

@Serializable
data class VariantResponse(
    val isOriginalVariant: Boolean,
    val bucket: String,
    val storeKey: String,
    val attributes: AttributeResponse,
    val transformation: TransformationResponse?,
    val lqip: LQIPResponse,
) {
    companion object Factory {
        fun fromVariantData(variantData: VariantData): VariantResponse = VariantResponse(
            isOriginalVariant = variantData.isOriginalVariant,
            bucket = variantData.objectStoreBucket,
            storeKey = variantData.objectStoreKey,
            attributes = AttributeResponse.fromAttributes(variantData.attributes),
            lqip = LQIPResponse.fromLqips(variantData.lqips),
            transformation = if (variantData.isOriginalVariant) {
                null
            } else {
                TransformationResponse.fromTransformation(variantData.transformation)
            }
        )

        fun fromVariant(variant: Variant): VariantResponse = VariantResponse(
            isOriginalVariant = variant.isOriginalVariant,
            bucket = variant.objectStoreBucket,
            storeKey = variant.objectStoreKey,
            attributes = AttributeResponse.fromAttributes(variant.attributes),
            lqip = LQIPResponse.fromLqips(variant.lqips),
            transformation = if (variant.isOriginalVariant) {
                null
            } else {
                TransformationResponse.fromTransformation(variant.transformation)
            }
        )
    }
}

@Serializable
data class AttributeResponse(
    val height: Int,
    val width: Int,
    val mimeType: String,
    val pageCount: Int?,
    val loop: Int?,
) {
    companion object Factory {
        fun fromAttributes(attributes: Attributes): AttributeResponse = AttributeResponse(
            height = attributes.height,
            width = attributes.width,
            mimeType = attributes.format.mimeType,
            pageCount = attributes.pageCount,
            loop = attributes.loop,
        )
    }
}

@Serializable
data class TransformationResponse(
    val width: Int,
    val height: Int,
    val fit: Fit,
    val gravity: Gravity,
    val format: ImageFormat,
    val rotate: Rotate,
    val horizontalFlip: Boolean,
    val filter: Filter,
    val blur: Int,
    val quality: Int,
    val pad: Int,
    val background: List<Int> = emptyList(),
) {
    companion object Factory {
        fun fromTransformation(transformation: Transformation): TransformationResponse = TransformationResponse(
            width = transformation.width,
            height = transformation.height,
            fit = transformation.fit,
            gravity = transformation.gravity,
            format = transformation.format,
            rotate = transformation.rotate,
            horizontalFlip = transformation.horizontalFlip,
            filter = transformation.filter,
            blur = transformation.blur,
            quality = transformation.quality,
            pad = transformation.pad,
            background = transformation.background,
        )
    }
}

@Serializable
data class LQIPResponse(
    val blurhash: String?,
    val thumbhash: String?,
) {
    companion object Factory {
        fun fromLqips(lqips: LQIPs): LQIPResponse = LQIPResponse(
            blurhash = lqips.blurhash,
            thumbhash = lqips.thumbhash,
        )
    }
}

@Serializable
data class AssetLinkResponse(
    val url: String,
    val lqip: LQIPResponse,
)
