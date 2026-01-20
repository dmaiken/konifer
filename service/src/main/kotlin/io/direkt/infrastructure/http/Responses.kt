package io.direkt.infrastructure.http

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetClass
import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetSource
import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Flip
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.Rotate
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantData
import io.direkt.infrastructure.http.serialization.AssetClassSerializer
import io.direkt.infrastructure.http.serialization.AssetSourceSerializer
import io.direkt.infrastructure.http.serialization.FilterSerializer
import io.direkt.infrastructure.http.serialization.FitSerializer
import io.direkt.infrastructure.http.serialization.FlipSerializer
import io.direkt.infrastructure.http.serialization.GravitySerializer
import io.direkt.infrastructure.http.serialization.LocalDateTimeSerializer
import io.direkt.infrastructure.http.serialization.RotateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

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
    companion object Factory {
        fun fromAssetData(assetData: AssetData): AssetResponse =
            AssetResponse(
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

        fun fromAsset(asset: Asset): AssetResponse =
            AssetResponse(
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
    val storeBucket: String,
    val storeKey: String,
    val attributes: AttributeResponse,
    val transformation: TransformationResponse?,
    val lqip: LQIPResponse,
) {
    companion object Factory {
        fun fromVariantData(variantData: VariantData): VariantResponse =
            VariantResponse(
                isOriginalVariant = variantData.isOriginalVariant,
                storeBucket = variantData.objectStoreBucket,
                storeKey = variantData.objectStoreKey,
                attributes = AttributeResponse.fromAttributes(variantData.attributes),
                lqip = LQIPResponse.fromLqips(variantData.lqips),
                transformation =
                    if (variantData.isOriginalVariant) {
                        null
                    } else {
                        TransformationResponse.fromTransformation(variantData.transformation)
                    },
            )

        fun fromVariant(variant: Variant): VariantResponse =
            VariantResponse(
                isOriginalVariant = variant.isOriginalVariant,
                storeBucket = variant.objectStoreBucket,
                storeKey = variant.objectStoreKey,
                attributes = AttributeResponse.fromAttributes(variant.attributes),
                lqip = LQIPResponse.fromLqips(variant.lqips),
                transformation =
                    if (variant.isOriginalVariant) {
                        null
                    } else {
                        TransformationResponse.fromTransformation(variant.transformation)
                    },
            )
    }
}

@Serializable
data class AttributeResponse(
    val height: Int,
    val width: Int,
    val format: String,
    val pageCount: Int?,
    val loop: Int?,
) {
    companion object Factory {
        fun fromAttributes(attributes: Attributes): AttributeResponse =
            AttributeResponse(
                height = attributes.height,
                width = attributes.width,
                format = attributes.format.format,
                pageCount = attributes.pageCount,
                loop = attributes.loop,
            )
    }
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
    val pad: Int,
    val background: List<Int> = emptyList(),
) {
    companion object Factory {
        fun fromTransformation(transformation: Transformation): TransformationResponse =
            TransformationResponse(
                width = transformation.width,
                height = transformation.height,
                fit = transformation.fit,
                gravity = transformation.gravity,
                format = transformation.format.format,
                rotate = transformation.rotate,
                flip = if (transformation.horizontalFlip) Flip.H else Flip.NONE,
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
        fun fromLqips(lqips: LQIPs): LQIPResponse =
            LQIPResponse(
                blurhash = lqips.blurhash,
                thumbhash = lqips.thumbhash,
            )
    }
}

@Serializable
data class AssetLinkResponse(
    val url: String,
    val alt: String?,
    val lqip: LQIPResponse,
)
