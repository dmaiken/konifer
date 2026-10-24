package io.konifer.infrastructure.http

import io.konifer.common.asset.AssetClass
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.AttributesResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.http.MetadataResponse
import io.konifer.common.http.PaddingResponse
import io.konifer.common.http.PixelAttributesResponse
import io.konifer.common.http.TransformationResponse
import io.konifer.common.http.VariantResponse
import io.konifer.common.image.Flip
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.image.ExifOrientations
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import kotlin.time.toKotlinInstant

fun AssetResponse.Factory.fromAssetData(assetData: AssetData): AssetResponse =
    AssetResponse(
        `class` = AssetClass.IMAGE,
        alt = assetData.alt,
        entryId = assetData.entryId,
        labels = assetData.labels,
        tags = assetData.tags,
        source = assetData.source,
        sourceUrl = assetData.externalSourceAddress,
        externalSourceAddress = assetData.externalSourceAddress,
        variants = assetData.variants.map { VariantResponse.fromVariantData(it) },
        createdAt = assetData.createdAt.toKotlinInstant(),
        modifiedAt = assetData.modifiedAt.toKotlinInstant(),
    )

fun AssetResponse.Factory.fromAsset(asset: Asset): AssetResponse =
    AssetResponse(
        `class` = AssetClass.IMAGE,
        alt = asset.alt?.value,
        entryId = checkNotNull(asset.entryId),
        labels = asset.labels.asMap(),
        tags = asset.tags.asSet(),
        source = asset.source,
        sourceUrl = asset.externalSourceAddress,
        externalSourceAddress = asset.externalSourceAddress,
        variants = asset.variants.map { VariantResponse.fromVariant(it) },
        createdAt = asset.createdAt.toKotlinInstant(),
        modifiedAt = asset.modifiedAt.toKotlinInstant(),
    )

fun VariantResponse.Factory.fromVariantData(variantData: VariantData): VariantResponse =
    VariantResponse(
        isOriginalVariant = variantData.isOriginalVariant,
        storeBucket = variantData.objectStoreBucket,
        storeKey = variantData.objectStoreKey,
        attributes = AttributesResponse.fromAttributes(variantData.attributes),
        lqip = LQIPResponse.fromLqips(variantData.lqips),
        transformation =
            when (val specification = variantData.specification) {
                VariantSpecification.Original -> null
                is VariantSpecification.Transformed -> TransformationResponse.fromTransformation(specification.transformation)
            },
    )

fun VariantResponse.Factory.fromVariant(variant: Variant): VariantResponse =
    VariantResponse(
        isOriginalVariant = variant.isOriginalVariant,
        storeBucket = variant.objectStoreBucket,
        storeKey = variant.objectStoreKey,
        attributes = AttributesResponse.fromAttributes(variant.attributes),
        lqip = LQIPResponse.fromLqips(variant.lqips),
        transformation =
            when (val specification = variant.specification) {
                VariantSpecification.Original -> null
                is VariantSpecification.Transformed -> TransformationResponse.fromTransformation(specification.transformation)
            },
    )

fun AttributesResponse.Factory.fromAttributes(attributes: Attributes): AttributesResponse {
    val (rotate, horizontalFlip) = ExifOrientations.fromExifOrientation(attributes.orientation)

    return AttributesResponse(
        height = attributes.height.value,
        width = attributes.width.value,
        format = attributes.format.format,
        colorSpace = attributes.colorSpace.name,
        pageCount = attributes.pageCount,
        loop = attributes.loop,
        rotate = rotate,
        horizontalFlip = horizontalFlip,
        pixels =
            attributes.pixels?.let {
                PixelAttributesResponse(
                    channels = it.channels.value.lowercase(),
                    layout = it.layout,
                    dataType = it.dataType,
                )
            },
    )
}

fun TransformationResponse.Factory.fromTransformation(transformation: Transformation): TransformationResponse =
    TransformationResponse(
        width = transformation.width.value,
        height = transformation.height.value,
        fit = transformation.fit,
        gravity = transformation.gravity,
        format = transformation.format.format,
        rotate = transformation.rotate,
        flip = if (transformation.horizontalFlip) Flip.H else Flip.NONE,
        filter = transformation.filter,
        blur = transformation.blur.value,
        quality = transformation.quality.value,
        padding =
            PaddingResponse(
                amount = transformation.padding.amount.value,
                color = transformation.padding.color,
            ),
        metadata =
            MetadataResponse(
                strip = transformation.metadata.strip.toList(),
            ),
        colorSpace = transformation.colorSpace.name,
    )

fun LQIPResponse.Factory.fromLqips(lqips: LQIPs): LQIPResponse =
    LQIPResponse(
        blurhash = lqips.blurhash,
        thumbhash = lqips.thumbhash,
    )
