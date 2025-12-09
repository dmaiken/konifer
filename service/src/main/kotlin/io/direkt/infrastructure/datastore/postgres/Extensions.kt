package io.direkt.infrastructure.datastore.postgres

import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetTreeRecord
import direkt.jooq.tables.records.AssetVariantRecord
import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetId
import io.direkt.domain.asset.AssetSource
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantData
import io.direkt.domain.variant.VariantId
import io.direkt.infrastructure.http.serialization.format
import org.jooq.Field
import org.jooq.Record

fun <T : Any> Record.getNonNull(field: Field<T?>): T = checkNotNull(this.get(field)) { "Field '${field.name}' is null" }

fun AssetTreeRecord.toAssetData(
    variants: List<AssetVariantRecord>,
    labels: List<AssetLabelRecord>, 
    tags: List<AssetTagRecord>,
): AssetData = AssetData(
    id = AssetId(checkNotNull(id)),
    path = checkNotNull(path).data(),
    entryId = checkNotNull(entryId),
    alt = checkNotNull(alt),
    labels = labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) },
    tags = tags.mapNotNull { it.tagValue }.toSet(),
    source = AssetSource.valueOf(checkNotNull(source)),
    sourceUrl = sourceUrl,
    createdAt = checkNotNull(createdAt),
    modifiedAt = checkNotNull(modifiedAt),
    variants = variants.map { it.toVariantData() },
)

fun AssetVariantRecord.toVariantData(): VariantData = VariantData(
    id = VariantId(checkNotNull(id)),
    objectStoreBucket = checkNotNull(objectStoreBucket),
    objectStoreKey = checkNotNull(objectStoreKey),
    isOriginalVariant = originalVariant ?: false,
    attributes = format
        .decodeFromString<ImageVariantAttributes>(
            checkNotNull(attributes).data(),
        ).toAttributes(),
    transformation = format.decodeFromString<ImageVariantTransformation>(
        checkNotNull(transformation).data(),
    ).toTransformation(),
    lqips = format.decodeFromString(checkNotNull(lqip).data()),
    createdAt = checkNotNull(createdAt),
    uploadedAt = null,
)

fun AssetVariantRecord.toPendingVariant(): Variant.Pending = Variant.Pending(
    id = VariantId(checkNotNull(id)),
    assetId = AssetId(checkNotNull(assetId)),
    objectStoreBucket = checkNotNull(objectStoreBucket),
    objectStoreKey = checkNotNull(objectStoreKey),
    isOriginalVariant = originalVariant ?: false,
    attributes = format
        .decodeFromString<ImageVariantAttributes>(
            checkNotNull(attributes).data(),
        ).toAttributes(),
    transformation = format.decodeFromString<ImageVariantTransformation>(
        checkNotNull(transformation).data(),
    ).toTransformation(),
    lqips = format.decodeFromString(checkNotNull(lqip).data()),
    createdAt = checkNotNull(createdAt),
    uploadedAt = null,
)
