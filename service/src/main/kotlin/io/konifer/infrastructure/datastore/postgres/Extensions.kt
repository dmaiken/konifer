package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.asset.AssetSource
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.AssetTags
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.asset.toAssetAlt
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.asset.toAssetTags
import io.konifer.domain.asset.toEntryId
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.infrastructure.datastore.postgres.schema.ImageVariantAttributes
import io.konifer.infrastructure.datastore.postgres.schema.ImageVariantTransformation
import io.ktor.util.logging.KtorSimpleLogger
import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.SerializationException
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val transformationLogger = KtorSimpleLogger("io.konifer.infrastructure.datastore.postgres.StoredTransformation")

fun <T : Any> Record.getNonNull(field: Field<T?>): T = checkNotNull(this.get(field)) { "Field '${field.name}' is null" }

suspend fun <T> DSLContext.contextualizedTransactionCoroutine(
    advisoryLockKey: Long? = null,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    transactional: suspend context(DSLContext) (Configuration) -> T,
): T =
    transactionCoroutine(coroutineContext) { trx ->
        advisoryLockKey?.let { key ->
            // Acquire transaction-scoped advisory lock
            trx
                .dsl()
                .resultQuery(
                    "select pg_advisory_xact_lock(?)",
                    key,
                ).awaitSingle()
        }
        context(trx.dsl()) {
            transactional(trx)
        }
    }

fun AssetTreeRecord.toAssetData(
    variants: List<AssetVariantRecord>,
    labels: List<AssetLabelRecord>,
    tags: List<AssetTagRecord>,
): AssetData =
    AssetData(
        id = AssetId(checkNotNull(id)),
        path = checkNotNull(path).toAssetPath(),
        entryId = checkNotNull(entryId).toEntryId(),
        alt = alt,
        labels = labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) },
        tags = tags.mapNotNull { it.tagValue }.toSet(),
        source = AssetSource.valueOf(checkNotNull(source)),
        externalSourceAddress = sourceUrl,
        isReady = isReady ?: false,
        createdAt = checkNotNull(createdAt).toInstant(),
        modifiedAt = checkNotNull(modifiedAt).toInstant(),
        variants = variants.mapNotNull { variant -> variant.toVariantSpecificationOrNull()?.let { variant.toVariantData(it) } },
    )

fun AssetVariantRecord.toVariantData(specification: VariantSpecification = toVariantSpecification()): VariantData =
    VariantData(
        id = VariantId(checkNotNull(id)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        attributes =
            postgresMetadataJson
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        specification = specification,
        lqips = postgresMetadataJson.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt).toInstant(),
        uploadedAt = uploadedAt?.toInstant(),
        expiresAt = expiresAt?.toInstant(),
        lastAccessedAt = lastAccessedAt?.toInstant(),
    )

fun AssetTreeRecord.toPendingPersisted(
    variant: AssetVariantRecord,
    labels: AssetLabels,
    tags: AssetTags,
): Asset.PendingPersisted =
    Asset.PendingPersisted(
        id = AssetId(checkNotNull(id)),
        path = checkNotNull(path).toAssetPath(),
        entryId = checkNotNull(entryId).toEntryId(),
        alt = alt?.toAssetAlt(),
        labels = labels,
        tags = tags,
        source = AssetSource.valueOf(checkNotNull(source)),
        externalSourceAddress = sourceUrl,
        createdAt = checkNotNull(createdAt).toInstant(),
        modifiedAt = checkNotNull(modifiedAt).toInstant(),
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
        path = checkNotNull(path).toAssetPath(),
        entryId = checkNotNull(entryId).toEntryId(),
        alt = alt?.toAssetAlt(),
        labels = labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) }.toAssetLabels(),
        tags = tags.mapNotNull { it.tagValue }.toSet().toAssetTags(),
        source = AssetSource.valueOf(checkNotNull(source)),
        externalSourceAddress = sourceUrl,
        createdAt = checkNotNull(createdAt).toInstant(),
        modifiedAt = checkNotNull(modifiedAt).toInstant(),
        variants =
            variants
                .mapNotNull { variant ->
                    variant.toVariantSpecificationOrNull()?.let { specification ->
                        if (variant.modified()) {
                            variant.toReadyVariant(checkNotNull(variant.uploadedAt).toInstant(), specification)
                        } else {
                            variant.toPendingVariant(specification)
                        }
                    }
                }.toMutableList(),
        isReady = true,
    )

fun AssetVariantRecord.toPendingVariant(specification: VariantSpecification = toVariantSpecification()): Variant.Pending =
    Variant.Pending(
        id = VariantId(checkNotNull(id)),
        assetId = AssetId(checkNotNull(assetId)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        attributes =
            postgresMetadataJson
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        specification = specification,
        lqips = postgresMetadataJson.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt).toInstant(),
        uploadedAt = null,
        expiresAt = expiresAt?.toInstant(),
    )

fun AssetVariantRecord.toReadyVariant(
    uploadedAt: Instant,
    specification: VariantSpecification = toVariantSpecification(),
): Variant.Ready =
    Variant.Ready(
        id = VariantId(checkNotNull(id)),
        assetId = AssetId(checkNotNull(assetId)),
        objectStoreBucket = checkNotNull(objectStoreBucket),
        objectStoreKey = checkNotNull(objectStoreKey),
        attributes =
            postgresMetadataJson
                .decodeFromString<ImageVariantAttributes>(
                    checkNotNull(attributes).data(),
                ).toAttributes(),
        specification = specification,
        lqips = postgresMetadataJson.decodeFromString(checkNotNull(lqip).data()),
        createdAt = checkNotNull(createdAt).toInstant(),
        uploadedAt = uploadedAt,
        expiresAt = expiresAt?.toInstant(),
    )

fun AssetVariantRecord.toVariantSpecification(): VariantSpecification =
    if (originalVariant == true) {
        VariantSpecification.Original
    } else {
        VariantSpecification.Transformed(
            postgresJson
                .decodeFromString<ImageVariantTransformation>(
                    checkNotNull(transformation).data(),
                ).toTransformation(),
        )
    }

/** Only transformation serialization failures make a stored derived variant ineligible for reads. */
fun AssetVariantRecord.toVariantSpecificationOrNull(): VariantSpecification? =
    try {
        toVariantSpecification()
    } catch (e: SerializationException) {
        transformationLogger.warn("Ignoring unreadable transformation for asset $assetId, variant $id: ${e.message}")
        null
    }
