package io.direkt.infrastructure.datastore.postgres

import direkt.jooq.indexes.ASSET_VARIANT_TRANSFORMATION_UQ
import direkt.jooq.keys.ASSET_VARIANT__FK_ASSET_VARIANT_ASSET_ID_ASSET_TREE_ID
import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetVariantRecord
import direkt.jooq.tables.references.ASSET_LABEL
import direkt.jooq.tables.references.ASSET_TAG
import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantBucketAndKey
import io.direkt.service.context.OrderBy
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SortField
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.jooq.postgres.extensions.types.Ltree
import java.time.LocalDateTime
import java.util.UUID

class PostgresAssetRepository(
    private val dslContext: DSLContext,
) : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted {
        val now = LocalDateTime.now()
        val treePath = LtreePathAdapter.toTreePathFromUriPath(asset.path)
        val originalVariant = asset.variants.first()
        val transformations =
            VariantParameterGenerator.generateImageVariantTransformations(
                attributes = originalVariant.attributes,
            )
        val attributes = VariantParameterGenerator.generateImageVariantAttributes(originalVariant.attributes)
        val assetId = asset.id.value
        return dslContext.transactionCoroutine { trx ->
            val entryId = getNextEntryId(trx.dsl(), treePath)
            logger.info("Calculated entry_id: $entryId when storing new asset with path: $treePath")
            val insert =
                trx
                    .dsl()
                    .insertInto(ASSET_TREE)
                    .set(ASSET_TREE.ID, assetId)
                    .set(ASSET_TREE.PATH, treePath)
                    .set(ASSET_TREE.ALT, asset.alt)
                    .set(ASSET_TREE.ENTRY_ID, entryId)
                    .set(ASSET_TREE.SOURCE, asset.source.toString())
                    .set(ASSET_TREE.CREATED_AT, now)
                    .set(ASSET_TREE.MODIFIED_AT, now)
            asset.sourceUrl?.let {
                insert.set(ASSET_TREE.SOURCE_URL, it)
            }
            val persistedAsset = insert.returning().awaitFirst()
            insertLabels(trx.dsl(), assetId, asset.labels, now)
            insertTags(trx.dsl(), assetId, asset.tags, now)

            val lqip = format.encodeToString(asset.variants.first().lqips)
            val persistedVariant =
                trx
                    .dsl()
                    .insertInto(ASSET_VARIANT)
                    .set(ASSET_VARIANT.ID, UUID.randomUUID())
                    .set(ASSET_VARIANT.ASSET_ID, assetId)
                    .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, originalVariant.objectStoreBucket)
                    .set(ASSET_VARIANT.OBJECT_STORE_KEY, originalVariant.objectStoreKey)
                    .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(attributes))
                    .set(ASSET_VARIANT.TRANSFORMATION, JSONB.valueOf(transformations))
                    .set(ASSET_VARIANT.LQIP, JSONB.valueOf(lqip))
                    .set(ASSET_VARIANT.ORIGINAL_VARIANT, true)
                    .set(ASSET_VARIANT.CREATED_AT, now)
                    .returning()
                    .awaitFirst()

            persistedAsset.toPendingPersisted(persistedVariant, asset.labels, asset.tags)
        }
    }

    override suspend fun markReady(asset: Asset.Ready) {
        val originalVariant = asset.variants.first { it.isOriginalVariant }
        dslContext.transactionCoroutine { trx ->
            trx
                .dsl()
                .update(ASSET_TREE)
                .set(ASSET_TREE.IS_READY, true)
                .set(ASSET_TREE.MODIFIED_AT, asset.modifiedAt)
                .where(ASSET_TREE.ID.eq(asset.id.value))
                .awaitFirstOrNull()

            trx
                .dsl()
                .update(ASSET_VARIANT)
                .set(ASSET_VARIANT.UPLOADED_AT, originalVariant.uploadedAt)
                .where(ASSET_VARIANT.ASSET_ID.eq(originalVariant.assetId.value))
                .and(ASSET_VARIANT.ORIGINAL_VARIANT.eq(true))
                .awaitFirstOrNull()
        }
    }

    override suspend fun markUploaded(variant: Variant.Ready) {
        dslContext
            .update(ASSET_VARIANT)
            .set(ASSET_VARIANT.UPLOADED_AT, variant.uploadedAt)
            .where(ASSET_VARIANT.ASSET_ID.eq(variant.assetId.value))
            .awaitFirstOrNull()
    }

    override suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending =
        dslContext.transactionCoroutine { trx ->
            val transformations =
                VariantParameterGenerator.generateImageVariantTransformations(
                    variant.transformation,
                )
            val attributes = VariantParameterGenerator.generateImageVariantAttributes(variant.attributes)
            val lqip = format.encodeToString(variant.lqips)

            val persistedVariant =
                try {
                    trx
                        .dsl()
                        .insertInto(ASSET_VARIANT)
                        .set(ASSET_VARIANT.ID, UUID.randomUUID())
                        .set(ASSET_VARIANT.ASSET_ID, variant.assetId.value)
                        .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, variant.objectStoreBucket)
                        .set(ASSET_VARIANT.OBJECT_STORE_KEY, variant.objectStoreKey)
                        .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(attributes))
                        .set(ASSET_VARIANT.TRANSFORMATION, JSONB.valueOf(transformations))
                        .set(ASSET_VARIANT.LQIP, JSONB.valueOf(lqip))
                        .set(ASSET_VARIANT.ORIGINAL_VARIANT, false)
                        .set(ASSET_VARIANT.CREATED_AT, LocalDateTime.now())
                        .returning()
                        .awaitFirst()
                } catch (e: IntegrityConstraintViolationException) {
                    if (e.message?.contains(ASSET_VARIANT_TRANSFORMATION_UQ.name) == true) {
                        throw IllegalArgumentException("Variant already exists for assetId: ${variant.assetId}")
                    }
                    if (e.message?.contains(ASSET_VARIANT__FK_ASSET_VARIANT_ASSET_ID_ASSET_TREE_ID.name) == true) {
                        throw IllegalArgumentException("No asset exists for assetId: ${variant.assetId}")
                    }
                    throw e
                }

            persistedVariant.toPendingVariant()
        }

    override suspend fun fetchForUpdate(
        path: String,
        entryId: Long,
    ): Asset? =
        fetch(
            context = dslContext,
            treePath = LtreePathAdapter.toTreePathFromUriPath(path),
            entryId = entryId,
            transformation = null,
            orderBy = OrderBy.CREATED,
            includeOnlyReady = false,
        )?.let { fetched ->
            if (fetched.asset.isReady == true) {
                fetched.asset.toReadyAsset(
                    variants = fetched.variants,
                    labels = fetched.labels,
                    tags = fetched.tags,
                )
            } else {
                fetched.asset.toPendingPersisted(
                    variant = fetched.variants.first(),
                    labels = fetched.labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) },
                    tags = fetched.tags.mapNotNull { it.tagValue }.toSet(),
                )
            }
        }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
    ): AssetData? =
        fetch(
            context = dslContext,
            treePath = LtreePathAdapter.toTreePathFromUriPath(path),
            entryId = entryId,
            transformation = transformation,
            orderBy = orderBy,
            labels = labels,
            includeOnlyReady = includeOnlyReady,
        )?.let {
            it.asset.toAssetData(it.variants, it.labels, it.tags)
        }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        limit: Int,
    ): List<AssetData> {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        val variantsField = multisetVariantField(dslContext, transformation)
        val labelsField = multisetLabels(dslContext)
        val tagsField = multisetTags(dslContext)
        val whereCondition =
            includeReadyConditions(
                whereCondition =
                    appendLabelConditions(
                        whereCondition = ASSET_TREE.PATH.eq(treePath),
                        labels = labels,
                    ),
                onlyReady = true,
            )
        val orderByConditions = orderByConditions(orderBy)

        return dslContext
            .select(
                *ASSET_TREE.fields(),
                variantsField,
                labelsField,
                tagsField,
            ).from(ASSET_TREE)
            .where(whereCondition)
            .orderBy(*orderByConditions)
            .limit(limit)
            .asFlow()
            .map { record ->
                val assetTreeRecord = record.into(ASSET_TREE) // Get the main record fields
                val variants = record.getValue(variantsField)
                val labels = record.getValue(labelsField)
                val tags = record.getValue(tagsField)

                AssetRecordsDto(assetTreeRecord, variants, labels, tags)
            }.map { it.asset.toAssetData(it.variants, it.labels, it.tags) }
            .toList()
    }

    override suspend fun deleteAssetByPath(
        path: String,
        entryId: Long?,
    ): List<VariantBucketAndKey> {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        val objectStoreInformation =
            dslContext.transactionCoroutine { trx ->
                val entryIdCondition =
                    entryId?.let {
                        ASSET_TREE.ENTRY_ID.eq(entryId)
                    } ?: ASSET_TREE.ENTRY_ID.eq(
                        trx
                            .dsl()
                            .select(ASSET_TREE.ENTRY_ID)
                            .from(ASSET_TREE)
                            .where(ASSET_TREE.PATH.eq(treePath))
                            .orderBy(ASSET_TREE.CREATED_AT.desc())
                            .limit(1),
                    )
                val variantObjectStoreInformation =
                    trx
                        .dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT)
                        .on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .where(ASSET_TREE.PATH.eq(treePath))
                        .and(entryIdCondition)
                        .asFlow()
                        .map {
                            VariantBucketAndKey(
                                bucket = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                                key = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                            )
                        }.toList()

                trx
                    .dsl()
                    .deleteFrom(ASSET_TREE)
                    .where(ASSET_TREE.PATH.eq(treePath))
                    .and(entryIdCondition)
                    .awaitFirstOrNull()

                variantObjectStoreInformation
            }
        return objectStoreInformation
    }

    override suspend fun deleteAssetsByPath(
        path: String,
        recursive: Boolean,
    ) = coroutineScope {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        val deletedAssets =
            dslContext.transactionCoroutine { trx ->
                val objectStoreInformation =
                    trx
                        .dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT)
                        .on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .let {
                            if (recursive) {
                                it.where(ASSET_TREE.PATH.contains(treePath))
                            } else {
                                it.where(ASSET_TREE.PATH.eq(treePath))
                            }
                        }.asFlow()
                        .map {
                            VariantBucketAndKey(
                                bucket = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                                key = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                            )
                        }.toList()

                trx
                    .dsl()
                    .deleteFrom(ASSET_TREE)
                    .let {
                        if (recursive) {
                            it.where(ASSET_TREE.PATH.contains(treePath))
                        } else {
                            it.where(ASSET_TREE.PATH.eq(treePath))
                        }
                    }.awaitFirstOrNull()
                objectStoreInformation
            }

        deletedAssets
    }

    override suspend fun update(asset: Asset): Asset {
        val fetched =
            fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, OrderBy.CREATED)
                ?: throw IllegalStateException("Asset not found with path: ${asset.path}, entryId: ${asset.entryId}")

        val assetId = fetched.id
        var modified = false
        dslContext.transactionCoroutine { trx ->
            if (fetched.alt != asset.alt) {
                modified = true
                trx
                    .dsl()
                    .update(ASSET_TREE)
                    .set(ASSET_TREE.ALT, asset.alt)
                    .where(ASSET_TREE.ID.eq(assetId.value))
                    .awaitFirst()
            }
            if (fetched.labels != asset.labels) {
                modified = true
                trx
                    .dsl()
                    .delete(ASSET_LABEL)
                    .where(ASSET_LABEL.ASSET_ID.eq(assetId.value))
                    .awaitFirst()
                insertLabels(trx.dsl(), assetId.value, asset.labels)
            }
            if (fetched.tags != asset.tags) {
                modified = true
                trx
                    .dsl()
                    .delete(ASSET_TAG)
                    .where(ASSET_TAG.ASSET_ID.eq(assetId.value))
                    .awaitFirst()
                insertTags(trx.dsl(), assetId.value, asset.tags)
            }
            if (modified) {
                trx
                    .dsl()
                    .update(ASSET_TREE)
                    .set(ASSET_TREE.MODIFIED_AT, asset.modifiedAt)
                    .where(ASSET_TREE.ID.eq(assetId.value))
                    .awaitFirst()
            }
        }

        return if (modified) {
            fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, OrderBy.CREATED)
                ?.let { Asset.Ready.from(it) }
                ?: throw IllegalStateException("Asset does not exist after updating")
        } else {
            asset
        }
    }

    private suspend fun getNextEntryId(
        context: DSLContext,
        treePath: Ltree,
    ): Long {
        val maxField = DSL.max(ASSET_TREE.ENTRY_ID).`as`("max_entry")
        return context
            .select(maxField)
            .from(ASSET_TREE)
            .where(ASSET_TREE.PATH.eq(treePath))
            .awaitFirstOrNull()
            ?.get(maxField)
            ?.inc() ?: 0L
    }

    private suspend fun fetch(
        context: DSLContext,
        treePath: Ltree,
        entryId: Long?,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String> = emptyMap(),
        includeOnlyReady: Boolean = true,
    ): AssetRecordsDto? {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: DSL.noCondition()
        val assetOrderConditions = orderByConditions(orderBy)
        val whereCondition =
            includeReadyConditions(
                whereCondition =
                    appendLabelConditions(
                        whereCondition = ASSET_TREE.PATH.eq(treePath),
                        labels = labels,
                    ),
                onlyReady = includeOnlyReady,
            )
        val variantsField = multisetVariantField(context, transformation)
        val labelsField = multisetLabels(context)
        val tagsField = multisetTags(context)
        return context
            .select(
                *ASSET_TREE.fields(),
                variantsField,
                labelsField,
                tagsField,
            ).from(ASSET_TREE)
            .where(whereCondition)
            .and(entryIdCondition)
            .orderBy(*assetOrderConditions)
            .limit(1)
            .awaitFirstOrNull()
            ?.let { record ->
                val assetTreeRecord = record.into(ASSET_TREE)
                val variants = record.getValue(variantsField)
                val labels = record.getValue(labelsField)
                val tags = record.getValue(tagsField)

                AssetRecordsDto(assetTreeRecord, variants, labels, tags)
            }
    }

    private fun calculateJoinVariantConditions(transformation: Transformation): Condition {
        val condition = ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID)
        return if (transformation.originalVariant) {
            condition.and(ASSET_VARIANT.ORIGINAL_VARIANT).eq(true)
        } else {
            condition
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "width").eq(transformation.width.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "height").eq(transformation.height.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "format").eq(transformation.format.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "fit").eq(transformation.fit.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "rotate").eq(transformation.rotate.name))
                .and(
                    DSL
                        .jsonbGetAttributeAsText(
                            ASSET_VARIANT.TRANSFORMATION,
                            "horizontalFlip",
                        ).eq(transformation.horizontalFlip.toString()),
                ).and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "filter").eq(transformation.filter.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "gravity").eq(transformation.gravity.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "quality").eq(transformation.quality.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "blur").eq(transformation.blur.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "pad").eq(transformation.pad.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "background").eq(transformation.background.toString()))
        }
    }

    private fun multisetVariantField(
        context: DSLContext,
        transformation: Transformation?,
    ) = DSL
        .multiset(
            context
                .select(*ASSET_VARIANT.fields())
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
                .and(
                    transformation?.let {
                        calculateJoinVariantConditions(it)
                    } ?: DSL.noCondition(),
                ).orderBy(ASSET_VARIANT.CREATED_AT.desc()),
        ).convertFrom { records ->
            records.map { r -> r.into(AssetVariantRecord::class.java) }
        }.`as`("variants")

    private fun multisetLabels(context: DSLContext) =
        DSL
            .multiset(
                context
                    .select(*ASSET_LABEL.fields())
                    .from(ASSET_LABEL)
                    .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID)),
            ).convertFrom { records ->
                records.map { r -> r.into(AssetLabelRecord::class.java) }
            }.`as`("labels")

    private fun multisetTags(context: DSLContext) =
        DSL
            .multiset(
                context
                    .select(*ASSET_TAG.fields())
                    .from(ASSET_TAG)
                    .where(ASSET_TAG.ASSET_ID.eq(ASSET_TREE.ID)),
            ).convertFrom { records ->
                records.map { r -> r.into(AssetTagRecord::class.java) }
            }.`as`("tags")

    private fun appendLabelConditions(
        whereCondition: Condition,
        labels: Map<String, String>,
    ): Condition {
        var condition = whereCondition
        labels.forEach { label ->
            condition =
                condition.and(
                    DSL.exists(
                        DSL
                            .selectOne()
                            .from(ASSET_LABEL)
                            .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID))
                            .and(
                                ASSET_LABEL.LABEL_KEY
                                    .eq(label.key)
                                    .and(ASSET_LABEL.LABEL_VALUE.eq(label.value)),
                            ),
                    ),
                )
        }

        return condition
    }

    private fun includeReadyConditions(
        whereCondition: Condition,
        onlyReady: Boolean,
    ): Condition =
        if (onlyReady) {
            whereCondition.and(ASSET_TREE.IS_READY.eq(true))
        } else {
            DSL.noCondition()
        }

    private suspend fun insertLabels(
        context: DSLContext,
        assetId: UUID,
        labels: Map<String, String>,
        dateTime: LocalDateTime = LocalDateTime.now(),
    ): List<AssetLabelRecord> {
        val updated = mutableListOf<AssetLabelRecord>()
        if (labels.isNotEmpty()) {
            val step =
                context.insertInto(
                    ASSET_LABEL,
                    ASSET_LABEL.ID,
                    ASSET_LABEL.ASSET_ID,
                    ASSET_LABEL.LABEL_KEY,
                    ASSET_LABEL.LABEL_VALUE,
                    ASSET_LABEL.CREATED_AT,
                )
            labels.forEach { (key, value) ->
                step.values(UUID.randomUUID(), assetId, key, value, dateTime)
            }
            step.returning().asFlow().toList(updated)
        }
        return updated
    }

    private suspend fun insertTags(
        context: DSLContext,
        assetId: UUID,
        tags: Set<String>,
        dateTime: LocalDateTime = LocalDateTime.now(),
    ) {
        val updated = mutableListOf<AssetTagRecord>()
        if (tags.isNotEmpty()) {
            val step =
                context.insertInto(
                    ASSET_TAG,
                    ASSET_TAG.ID,
                    ASSET_TAG.ASSET_ID,
                    ASSET_TAG.TAG_VALUE,
                    ASSET_TAG.CREATED_AT,
                )
            tags.forEach { value ->
                step.values(UUID.randomUUID(), assetId, value, dateTime)
            }
            step.returning().asFlow().toList(updated)
        }
    }

    private fun orderByConditions(orderBy: OrderBy): Array<out SortField<out Comparable<*>?>> {
        val orderByModifierCondition =
            when (orderBy) {
                OrderBy.CREATED -> ASSET_TREE.CREATED_AT.desc()
                OrderBy.MODIFIED -> ASSET_TREE.MODIFIED_AT.desc()
            }

        return arrayOf(orderByModifierCondition, ASSET_TREE.ENTRY_ID.desc())
    }
}
