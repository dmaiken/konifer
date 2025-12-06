package io.direkt.infrastructure.postgres

import direkt.jooq.indexes.ASSET_VARIANT_TRANSFORMATION_UQ
import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetVariantRecord
import direkt.jooq.tables.references.ASSET_LABEL
import direkt.jooq.tables.references.ASSET_TAG
import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.handler.dto.StoreAssetVariantDto
import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.asset.model.AssetAndVariants
import io.direkt.asset.model.VariantBucketAndKey
import io.direkt.infrastructure.postgres.AssetRecordsDto
import io.direkt.asset.variant.VariantParameterGenerator
import io.direkt.domain.image.Transformation
import io.direkt.domain.ports.AssetRepository
import io.direkt.serialization.format
import io.direkt.service.context.OrderBy
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
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

    override suspend fun store(asset: StoreAssetDto): AssetAndVariants {
        val assetId = UUID.randomUUID()
        val now = LocalDateTime.now()
        val treePath = PathAdapter.toTreePathFromUriPath(asset.path)
        val (transformations, transformationKey) = VariantParameterGenerator.generateImageVariantTransformations(asset.attributes)
        val attributes = VariantParameterGenerator.generateImageVariantAttributes(asset.attributes)
        return dslContext.transactionCoroutine { trx ->
            val entryId = getNextEntryId(trx.dsl(), treePath)
            logger.info("Calculated entry_id: $entryId when storing new asset with path: $treePath")
            val insert =
                trx
                    .dsl()
                    .insertInto(ASSET_TREE)
                    .set(ASSET_TREE.ID, assetId)
                    .set(ASSET_TREE.PATH, treePath)
                    .set(ASSET_TREE.ALT, asset.request.alt)
                    .set(ASSET_TREE.ENTRY_ID, entryId)
                    .set(ASSET_TREE.SOURCE, asset.source.toString())
                    .set(ASSET_TREE.CREATED_AT, now)
                    .set(ASSET_TREE.MODIFIED_AT, now)
            asset.request.url?.let {
                insert.set(ASSET_TREE.SOURCE_URL, it)
            }
            val persistedAsset = insert.returning().awaitFirst()
            insertLabels(trx.dsl(), assetId, asset.request.labels, now)
            insertTags(trx.dsl(), assetId, asset.request.tags, now)

            val lqip = format.encodeToString(asset.lqips)
            val persistedVariant =
                trx
                    .dsl()
                    .insertInto(ASSET_VARIANT)
                    .set(ASSET_VARIANT.ID, UUID.randomUUID())
                    .set(ASSET_VARIANT.ASSET_ID, assetId)
                    .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, asset.persistResult.bucket)
                    .set(ASSET_VARIANT.OBJECT_STORE_KEY, asset.persistResult.key)
                    .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(attributes))
                    .set(ASSET_VARIANT.TRANSFORMATION, JSONB.valueOf(transformations))
                    .set(ASSET_VARIANT.TRANSFORMATION_KEY, transformationKey)
                    .set(ASSET_VARIANT.LQIP, JSONB.valueOf(lqip))
                    .set(ASSET_VARIANT.ORIGINAL_VARIANT, true)
                    .set(ASSET_VARIANT.CREATED_AT, now)
                    .returning()
                    .awaitFirst()

            AssetAndVariants.Factory.from(persistedAsset, persistedVariant, asset.request.labels, asset.request.tags)
        }
    }

    override suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants {
        val treePath = PathAdapter.toTreePathFromUriPath(variant.path)
        return dslContext.transactionCoroutine { trx ->
            val asset =
                fetch(
                    trx.dsl(),
                    treePath,
                    variant.entryId,
                    Transformation.Factory.ORIGINAL_VARIANT,
                    OrderBy.CREATED,
                )
            if (asset == null) {
                throw IllegalArgumentException(
                    "Asset with path: $treePath and entry id: ${variant.entryId} not found in database",
                )
            }
            val (transformations, transformationsKey) =
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
                        .set(ASSET_VARIANT.ASSET_ID, asset.asset.id)
                        .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, variant.persistResult.bucket)
                        .set(ASSET_VARIANT.OBJECT_STORE_KEY, variant.persistResult.key)
                        .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(attributes))
                        .set(ASSET_VARIANT.TRANSFORMATION, JSONB.valueOf(transformations))
                        .set(ASSET_VARIANT.TRANSFORMATION_KEY, transformationsKey)
                        .set(ASSET_VARIANT.LQIP, JSONB.valueOf(lqip))
                        .set(ASSET_VARIANT.ORIGINAL_VARIANT, false)
                        .set(ASSET_VARIANT.CREATED_AT, LocalDateTime.now())
                        .returning()
                        .awaitFirst()
                } catch (e: IntegrityConstraintViolationException) {
                    if (e.message?.contains(ASSET_VARIANT_TRANSFORMATION_UQ.name) == true) {
                        throw IllegalArgumentException(
                            "Variant already exists for asset with entry_id: ${variant.entryId} at " +
                                "path: $treePath and attributes: ${variant.attributes}",
                        )
                    }
                    throw e
                }

            AssetAndVariants.Factory.from(asset.asset, listOf(persistedVariant), asset.labels, asset.tags)
        }
    }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
    ): AssetAndVariants? {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return fetch(dslContext, treePath, entryId, transformation, orderBy, labels)?.let {
            AssetAndVariants.Factory.from(it.asset, it.variants, it.labels, it.tags)
        }
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        limit: Int,
    ): List<AssetAndVariants> {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return fetchAllAtPath(dslContext, treePath, transformation, orderBy, labels, limit)
            .map { AssetAndVariants.Factory.from(it.asset, it.variants, it.labels, it.tags) }
    }

    override suspend fun deleteAssetByPath(
        path: String,
        entryId: Long?,
    ): List<VariantBucketAndKey> {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
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
        val treePath = PathAdapter.toTreePathFromUriPath(path)
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

    override suspend fun update(asset: UpdateAssetDto): AssetAndVariants {
        val fetched =
            fetchByPath(asset.path, asset.entryId, Transformation.Factory.ORIGINAL_VARIANT, OrderBy.CREATED)
                ?: throw IllegalStateException("Asset not found with path: ${asset.path}, entryId: ${asset.entryId}")

        val assetId = fetched.asset.id
        var modified = false
        dslContext.transactionCoroutine { trx ->
            if (fetched.asset.alt != asset.request.alt) {
                modified = true
                trx
                    .dsl()
                    .update(ASSET_TREE)
                    .set(ASSET_TREE.ALT, asset.request.alt)
                    .where(ASSET_TREE.ID.eq(assetId))
                    .awaitFirst()
            }
            if (fetched.asset.labels != asset.request.labels) {
                modified = true
                trx
                    .dsl()
                    .delete(ASSET_LABEL)
                    .where(ASSET_LABEL.ASSET_ID.eq(assetId))
                    .awaitFirst()
                insertLabels(trx.dsl(), assetId, asset.request.labels)
            }
            if (fetched.asset.tags != asset.request.tags) {
                modified = true
                trx
                    .dsl()
                    .delete(ASSET_TAG)
                    .where(ASSET_TAG.ASSET_ID.eq(assetId))
                    .awaitFirst()
                insertTags(trx.dsl(), assetId, asset.request.tags)
            }
            if (modified) {
                trx
                    .dsl()
                    .update(ASSET_TREE)
                    .set(ASSET_TREE.MODIFIED_AT, LocalDateTime.now())
                    .where(ASSET_TREE.ID.eq(assetId))
                    .awaitFirst()
            }
        }

        return if (modified) {
            fetchByPath(asset.path, asset.entryId, Transformation.Factory.ORIGINAL_VARIANT, OrderBy.CREATED)
                ?: throw IllegalStateException("Asset does not exist after updating")
        } else {
            fetched
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
    ): AssetRecordsDto? {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: DSL.noCondition()
        val assetOrderConditions = orderByConditions(orderBy)
        val whereCondition = appendLabelConditions(ASSET_TREE.PATH.eq(treePath), labels)
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

    private suspend fun fetchAllAtPath(
        context: DSLContext,
        treePath: Ltree,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        limit: Int,
    ): List<AssetRecordsDto> {
        val variantsField = multisetVariantField(context, transformation)
        val labelsField = multisetLabels(context)
        val tagsField = multisetTags(context)
        val whereCondition = appendLabelConditions(ASSET_TREE.PATH.eq(treePath), labels)
        val orderByConditions = orderByConditions(orderBy)

        return context
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
            }.toList()
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
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "horizontalFlip").eq(transformation.horizontalFlip.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "filter").eq(transformation.filter.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "gravity").eq(transformation.gravity.name))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "quality").eq(transformation.quality.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "pad").eq(transformation.pad.toString()))
                .and(DSL.jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "background").eq(transformation.background.toString()))
        }
    }

    private fun multisetVariantField(
        context: DSLContext,
        transformation: Transformation?,
    ) = DSL.multiset(
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
        DSL.multiset(
            context
                .select(*ASSET_LABEL.fields())
                .from(ASSET_LABEL)
                .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID)),
        ).convertFrom { records ->
            records.map { r -> r.into(AssetLabelRecord::class.java) }
        }.`as`("labels")

    private fun multisetTags(context: DSLContext) =
        DSL.multiset(
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
                        DSL.selectOne()
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

    private suspend fun insertLabels(
        context: DSLContext,
        assetId: UUID,
        labels: Map<String, String>,
        dateTime: LocalDateTime = LocalDateTime.now(),
    ) {
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
            step.awaitLast()
        }
    }

    private suspend fun insertTags(
        context: DSLContext,
        assetId: UUID,
        tags: Set<String>,
        dateTime: LocalDateTime = LocalDateTime.now(),
    ) {
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
            step.awaitLast()
        }
    }

    fun orderByConditions(orderBy: OrderBy): Array<out SortField<out Comparable<*>?>> {
        val orderByModifierCondition =
            when (orderBy) {
                OrderBy.CREATED -> ASSET_TREE.CREATED_AT.desc()
                OrderBy.MODIFIED -> ASSET_TREE.MODIFIED_AT.desc()
            }

        return arrayOf(orderByModifierCondition, ASSET_TREE.ENTRY_ID.desc())
    }
}