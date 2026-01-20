package io.konifer.infrastructure.datastore.inmemory

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData

fun Asset.toAssetData(variants: List<Variant>): AssetData =
    AssetData(
        id = id,
        path = path,
        entryId = checkNotNull(entryId),
        alt = alt,
        labels = labels,
        tags = tags,
        source = source,
        sourceUrl = sourceUrl,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        variants = variants.map { it.toVariantData() },
        isReady = isReady,
    )

fun Variant.toVariantData(): VariantData =
    VariantData(
        id = id,
        objectStoreBucket = objectStoreBucket,
        objectStoreKey = objectStoreKey,
        attributes = attributes,
        transformation = transformation,
        lqips = lqips,
        createdAt = createdAt,
        uploadedAt = uploadedAt,
        isOriginalVariant = isOriginalVariant,
    )
