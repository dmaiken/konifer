package io.direkt.infrastructure.datastore.inmemory

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantData

fun Asset.toAssetData(): AssetData =
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
