package io.konifer.infrastructure.http

import io.konifer.common.asset.AssetClass
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.AttributeResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.http.PaddingResponse
import io.konifer.common.http.TransformationResponse
import io.konifer.common.http.VariantResponse
import io.konifer.common.image.Flip
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import kotlinx.datetime.toKotlinLocalDateTime

fun AssetResponse.Factory.fromAssetData(assetData: AssetData): AssetResponse =
    AssetResponse(
        `class` = AssetClass.IMAGE,
        alt = assetData.alt,
        entryId = assetData.entryId,
        labels = assetData.labels,
        tags = assetData.tags,
        source = assetData.source,
        sourceUrl = assetData.sourceUrl,
        variants = assetData.variants.map { VariantResponse.fromVariantData(it) },
        createdAt = assetData.createdAt.toKotlinLocalDateTime(),
        modifiedAt = assetData.modifiedAt.toKotlinLocalDateTime(),
    )

fun AssetResponse.Factory.fromAsset(asset: Asset): AssetResponse =
    AssetResponse(
        `class` = AssetClass.IMAGE,
        alt = asset.alt,
        entryId = checkNotNull(asset.entryId),
        labels = asset.labels,
        tags = asset.tags,
        source = asset.source,
        sourceUrl = asset.sourceUrl,
        variants = asset.variants.map { VariantResponse.fromVariant(it) },
        createdAt = asset.createdAt.toKotlinLocalDateTime(),
        modifiedAt = asset.modifiedAt.toKotlinLocalDateTime(),
    )

fun VariantResponse.Factory.fromVariantData(variantData: VariantData): VariantResponse =
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

fun VariantResponse.Factory.fromVariant(variant: Variant): VariantResponse =
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

fun AttributeResponse.Factory.fromAttributes(attributes: Attributes): AttributeResponse =
    AttributeResponse(
        height = attributes.height,
        width = attributes.width,
        format = attributes.format.format,
        pageCount = attributes.pageCount,
        loop = attributes.loop,
    )

fun TransformationResponse.Factory.fromTransformation(transformation: Transformation): TransformationResponse =
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
        padding =
            PaddingResponse(
                amount = transformation.padding.amount,
                color = transformation.padding.color,
            ),
    )

fun LQIPResponse.Factory.fromLqips(lqips: LQIPs): LQIPResponse =
    LQIPResponse(
        blurhash = lqips.blurhash,
        thumbhash = lqips.thumbhash,
    )
