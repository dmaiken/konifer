package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.AssetSource
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantId
import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord
import org.jooq.Field
import org.jooq.Record
import java.time.LocalDateTime

fun <T : Any> Record.getNonNull(field: Field<T?>): T = checkNotNull(this.get(field)) { "Field '${field.name}' is null" }

fun AssetTreeRecord.toAssetData(
    variants: List<AssetVariantRecord>,
    labels: List<AssetLabelRecord>,
    tags: List<AssetTagRecord>,
): AssetData =
    AssetData(
        id = AssetId(checkNotNull(id)),
        path = LtreePathAdapter.toUriPath(checkNotNull(path).data()),
        entryId = checkNotNull(entryId),
        alt = checkNotNull(alt),
        labels = labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) },
        tags = tags.mapNotNull { it.tagValue }.toSet(),
        source = AssetSource.valueOf(checkNotNull(source)),
        sourceUrl = sourceUrl,
        isReady = isReady ?: false,
        createdAt = checkNotNull(createdAt),
        modifiedAt = checkNotNull(modifiedAt),
        variants = variants.map { it.toVariantData() },
    )

fun AssetVariantRecord.toVariantData(): VariantData =
    VariantData(
        id = VariantId(checkNotNull(id)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        isOriginalVariant = originalVariant ?: false,
        attributes =
            format
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        transformation =
            format
                .decodeFromString<ImageVariantTransformation>(
                    checkNotNull(transformation).data(),
                ).toTransformation(),
        lqips = format.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt),
        uploadedAt = uploadedAt,
    )

fun AssetTreeRecord.toPendingPersisted(
    variant: AssetVariantRecord,
    labels: Map<String, String>,
    tags: Set<String>,
): Asset.PendingPersisted =
    Asset.PendingPersisted(
        id = AssetId(checkNotNull(id)),
        path = LtreePathAdapter.toUriPath(checkNotNull(path).data()),
        entryId = checkNotNull(entryId),
        alt = alt,
        labels = labels,
        tags = tags,
        source = AssetSource.valueOf(checkNotNull(source)),
        sourceUrl = sourceUrl,
        createdAt = checkNotNull(createdAt),
        modifiedAt = checkNotNull(modifiedAt),
        isReady = false,
        variants = mutableListOf(variant.toPendingVariant()),
    )

fun AssetTreeRecord.toReadyAsset(
    variants: List<AssetVariantRecord>,
    labels: List<AssetLabelRecord>,
    tags: List<AssetTagRecord>,
): Asset.Ready =
    Asset.Ready(
        id = AssetId(checkNotNull(id)),
        path = LtreePathAdapter.toUriPath(checkNotNull(path).data()),
        entryId = checkNotNull(entryId),
        alt = checkNotNull(alt),
        labels = labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) },
        tags = tags.mapNotNull { it.tagValue }.toSet(),
        source = AssetSource.valueOf(checkNotNull(source)),
        sourceUrl = sourceUrl,
        createdAt = checkNotNull(createdAt),
        modifiedAt = checkNotNull(modifiedAt),
        variants =
            variants
                .map {
                    if (it.modified()) {
                        // TODO replace with uploadedAt
                        it.toReadyVariant()
                    } else {
                        it.toPendingVariant()
                    }
                }.toMutableList(),
        isReady = true,
    )

fun AssetVariantRecord.toPendingVariant(): Variant.Pending =
    Variant.Pending(
        id = VariantId(checkNotNull(id)),
        assetId = AssetId(checkNotNull(assetId)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        isOriginalVariant = originalVariant ?: false,
        attributes =
            format
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        transformation =
            format
                .decodeFromString<ImageVariantTransformation>(
                    checkNotNull(transformation).data(),
                ).toTransformation(),
        lqips = format.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt),
        uploadedAt = null,
    )

fun AssetVariantRecord.toReadyVariant(): Variant.Ready =
    Variant.Ready(
        id = VariantId(checkNotNull(id)),
        assetId = AssetId(checkNotNull(assetId)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        isOriginalVariant = originalVariant ?: false,
        attributes =
            format
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        transformation =
            format
                .decodeFromString<ImageVariantTransformation>(
                    checkNotNull(transformation).data(),
                ).toTransformation(),
        lqips = format.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt),
        uploadedAt = LocalDateTime.now(),
    )
