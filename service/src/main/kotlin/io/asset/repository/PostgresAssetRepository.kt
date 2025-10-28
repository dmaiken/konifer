package io.asset.repository

import direkt.jooq.indexes.ASSET_VARIANT_TRANSFORMATION_UQ
import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetVariantRecord
import direkt.jooq.tables.references.ASSET_LABEL
import direkt.jooq.tables.references.ASSET_TAG
import direkt.jooq.tables.references.ASSET_TREE
import direkt.jooq.tables.references.ASSET_VARIANT
import io.asset.handler.StoreAssetDto
import io.asset.handler.StoreAssetVariantDto
import io.asset.model.AssetAndVariants
import io.asset.model.VariantBucketAndKey
import io.asset.variant.VariantParameterGenerator
import io.image.model.Transformation
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.serialization.json.Json
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.jsonbGetAttributeAsText
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.multiset
import org.jooq.impl.DSL.noCondition
import org.jooq.impl.DSL.selectOne
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
            val persistedAsset =
                trx.dsl().insertInto(ASSET_TREE)
                    .set(ASSET_TREE.ID, assetId)
                    .set(ASSET_TREE.PATH, treePath)
                    .set(ASSET_TREE.ALT, asset.request.alt)
                    .set(ASSET_TREE.ENTRY_ID, entryId)
                    .set(ASSET_TREE.CREATED_AT, now)
                    .returning()
                    .awaitFirst()

            if (asset.request.labels.isNotEmpty()) {
                val step =
                    trx.dsl().insertInto(
                        ASSET_LABEL,
                        ASSET_LABEL.ID,
                        ASSET_LABEL.ASSET_ID,
                        ASSET_LABEL.LABEL_KEY,
                        ASSET_LABEL.LABEL_VALUE,
                        ASSET_LABEL.CREATED_AT,
                    )
                asset.request.labels.map { (key, value) ->
                    step.values(UUID.randomUUID(), assetId, key, value, now)
                }
                step.awaitLast()
            }
            if (asset.request.tags.isNotEmpty()) {
                val step =
                    trx.dsl().insertInto(
                        ASSET_TAG,
                        ASSET_TAG.ID,
                        ASSET_TAG.ASSET_ID,
                        ASSET_TAG.TAG_VALUE,
                        ASSET_TAG.CREATED_AT,
                    )
                asset.request.tags.map { value ->
                    step.values(UUID.randomUUID(), assetId, value, now)
                }
                step.awaitLast()
            }

            val lqip = Json.encodeToString(asset.lqips)
            val persistedVariant =
                trx.dsl().insertInto(ASSET_VARIANT)
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

            AssetAndVariants.from(persistedAsset, persistedVariant, asset.request.labels, asset.request.tags)
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
                    Transformation.ORIGINAL_VARIANT,
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
            val lqip = Json.encodeToString(variant.lqips)

            val persistedVariant =
                try {
                    trx.dsl().insertInto(ASSET_VARIANT)
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

            AssetAndVariants.from(asset.asset, listOf(persistedVariant), asset.labels, asset.tags)
        }
    }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        labels: Map<String, String>,
    ): AssetAndVariants? {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return fetch(dslContext, treePath, entryId, transformation, labels)?.let {
            AssetAndVariants.from(it.asset, it.variants, it.labels, it.tags)
        }
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        labels: Map<String, String>,
    ): List<AssetAndVariants> {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return fetchAllAtPath(dslContext, treePath, transformation, labels)
            .map { AssetAndVariants.from(it.asset, it.variants, it.labels, it.tags) }
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
                        trx.dsl().select(ASSET_TREE.ENTRY_ID)
                            .from(ASSET_TREE)
                            .where(ASSET_TREE.PATH.eq(treePath))
                            .orderBy(ASSET_TREE.CREATED_AT.desc())
                            .limit(1),
                    )
                val variantObjectStoreInformation =
                    trx.dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT).on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .where(ASSET_TREE.PATH.eq(treePath))
                        .and(entryIdCondition)
                        .asFlow()
                        .map {
                            VariantBucketAndKey(
                                bucket = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                                key = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                            )
                        }.toList()

                trx.dsl()
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
                    trx.dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT).on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .let {
                            if (recursive) {
                                it.where(ASSET_TREE.PATH.contains(treePath))
                            } else {
                                it.where(ASSET_TREE.PATH.eq(treePath))
                            }
                        }
                        .asFlow()
                        .map {
                            VariantBucketAndKey(
                                bucket = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                                key = it.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                            )
                        }
                        .toList()

                trx.dsl().deleteFrom(ASSET_TREE)
                    .let {
                        if (recursive) {
                            it.where(ASSET_TREE.PATH.contains(treePath))
                        } else {
                            it.where(ASSET_TREE.PATH.eq(treePath))
                        }
                    }
                    .awaitFirstOrNull()
                objectStoreInformation
            }

        deletedAssets
    }

    private suspend fun getNextEntryId(
        context: DSLContext,
        treePath: Ltree,
    ): Long {
        val maxField = max(ASSET_TREE.ENTRY_ID).`as`("max_entry")
        return context.select(maxField)
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
        labels: Map<String, String> = emptyMap(),
    ): AssetRecordsDto? {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: noCondition()
        val assetOrderConditions =
            entryId?.let {
                arrayOf(ASSET_TREE.ENTRY_ID.desc(), ASSET_TREE.CREATED_AT.desc())
            } ?: arrayOf(ASSET_TREE.CREATED_AT.desc())
        val whereCondition = appendLabelConditions(ASSET_TREE.PATH.eq(treePath), labels)
        val variantsField = multisetVariantField(context, transformation)
        val labelsField = multisetLabels(context)
        val tagsField = multisetTags(context)
        return context.select(
            *ASSET_TREE.fields(),
            variantsField,
            labelsField,
            tagsField,
        )
            .from(ASSET_TREE)
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
        labels: Map<String, String>,
    ): List<AssetRecordsDto> {
        val variantsField = multisetVariantField(context, transformation)
        val labelsField = multisetLabels(context)
        val tagsField = multisetTags(context)
        val whereCondition = appendLabelConditions(ASSET_TREE.PATH.eq(treePath), labels)
        return context.select(
            *ASSET_TREE.fields(),
            variantsField,
            labelsField,
            tagsField,
        )
            .from(ASSET_TREE)
            .where(whereCondition)
            .orderBy(ASSET_TREE.CREATED_AT.desc())
            .asFlow()
            .map { record ->
                val assetTreeRecord = record.into(ASSET_TREE) // Get the main record fields
                val variants = record.getValue(variantsField)
                val labels = record.getValue(labelsField)
                val tags = record.getValue(tagsField)

                AssetRecordsDto(assetTreeRecord, variants, labels, tags)
            }
            .toList()
    }

    private fun calculateJoinVariantConditions(transformation: Transformation): Condition {
        val condition = ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID)
        return if (transformation.originalVariant) {
            condition.and(ASSET_VARIANT.ORIGINAL_VARIANT).eq(true)
        } else {
            condition
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "width").eq(transformation.width.toString()))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "height").eq(transformation.height.toString()))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "format").eq(transformation.format.name))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "fit").eq(transformation.fit.name))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "rotate").eq(transformation.rotate.name))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "horizontalFlip").eq(transformation.horizontalFlip.toString()))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "filter").eq(transformation.filter.name))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "gravity").eq(transformation.gravity.name))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "quality").eq(transformation.quality.toString()))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "pad").eq(transformation.pad.toString()))
                .and(jsonbGetAttributeAsText(ASSET_VARIANT.TRANSFORMATION, "background").eq(transformation.background.toString()))
        }
    }

    private fun multisetVariantField(
        context: DSLContext,
        transformation: Transformation?,
    ) = multiset(
        context.select(*ASSET_VARIANT.fields())
            .from(ASSET_VARIANT)
            .where(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
            .and(
                transformation?.let {
                    calculateJoinVariantConditions(it)
                } ?: noCondition(),
            )
            .orderBy(ASSET_VARIANT.CREATED_AT.desc()),
    ).convertFrom { records ->
        records.map { r -> r.into(AssetVariantRecord::class.java) }
    }.`as`("variants")

    private fun multisetLabels(context: DSLContext) =
        multiset(
            context.select(*ASSET_LABEL.fields())
                .from(ASSET_LABEL)
                .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID)),
        ).convertFrom { records ->
            records.map { r -> r.into(AssetLabelRecord::class.java) }
        }.`as`("labels")

    private fun multisetTags(context: DSLContext) =
        multiset(
            context.select(*ASSET_TAG.fields())
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
                    exists(
                        selectOne()
                            .from(ASSET_LABEL)
                            .where(ASSET_LABEL.ASSET_ID.eq(ASSET_TREE.ID))
                            .and(
                                ASSET_LABEL.LABEL_KEY.eq(label.key)
                                    .and(ASSET_LABEL.LABEL_VALUE.eq(label.value)),
                            ),
                    ),
                )
        }

        return condition
    }
}
