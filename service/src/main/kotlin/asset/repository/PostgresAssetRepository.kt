package asset.repository

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import asset.variant.VariantParameterGenerator
import image.model.Transformation
import io.asset.handler.StoreAssetVariantDto
import io.asset.repository.PathAdapter
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.json.Json
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.jsonbGetAttributeAsText
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.noCondition
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.jooq.postgres.extensions.types.Ltree
import tessa.jooq.indexes.ASSET_VARIANT_TRANSFORMATION_UQ
import tessa.jooq.tables.records.AssetTreeRecord
import tessa.jooq.tables.references.ASSET_TREE
import tessa.jooq.tables.references.ASSET_VARIANT
import java.time.LocalDateTime
import java.util.UUID

class PostgresAssetRepository(
    private val dslContext: DSLContext,
    private val variantParameterGenerator: VariantParameterGenerator,
) : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun store(asset: StoreAssetDto): AssetAndVariants {
        val assetId = UUID.randomUUID()
        val now = LocalDateTime.now()
        val treePath = PathAdapter.toTreePathFromUriPath(asset.path)
        val (transformations, transformationKey) = variantParameterGenerator.generateImageVariantTransformations(asset.attributes)
        val attributes = variantParameterGenerator.generateImageVariantAttributes(asset.attributes)
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

            AssetAndVariants.from(persistedAsset, persistedVariant)
        }
    }

    override suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants {
        val treePath = PathAdapter.toTreePathFromUriPath(variant.path)
        return dslContext.transactionCoroutine { trx ->
            val asset =
                fetchWithVariant(
                    trx.dsl(),
                    treePath,
                    variant.entryId,
                    Transformation.ORIGINAL_VARIANT,
                )?.into(AssetTreeRecord::class.java)
            if (asset == null) {
                throw IllegalArgumentException(
                    "Asset with path: $treePath and entry id: ${variant.entryId} not found in database",
                )
            }
            val (transformations, transformationsKey) =
                variantParameterGenerator.generateImageVariantTransformations(
                    variant.transformation,
                )
            val attributes = variantParameterGenerator.generateImageVariantAttributes(variant.attributes)
            val lqip = Json.encodeToString(variant.lqips)

            val persistedVariant =
                try {
                    trx.dsl().insertInto(ASSET_VARIANT)
                        .set(ASSET_VARIANT.ID, UUID.randomUUID())
                        .set(ASSET_VARIANT.ASSET_ID, asset.id)
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

            AssetAndVariants.from(asset, persistedVariant)
        }
    }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
    ): AssetAndVariants? {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return if (transformation != null) {
            fetchWithVariant(dslContext, treePath, entryId, transformation)?.let { record ->
                AssetAndVariants.from(listOf(record))
            }
        } else {
            val result = fetchWithAllVariants(dslContext, treePath, entryId)
            AssetAndVariants.from(result)
        }
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
    ): List<AssetAndVariants> {
        val treePath = PathAdapter.toTreePathFromUriPath(path)
        return if (transformation != null) {
            fetchAllWithVariant(dslContext, treePath, transformation)
                .groupBy { it.get(ASSET_TREE.ID) }
                .values.mapNotNull { AssetAndVariants.from(it) }
        } else {
            fetchAllWithAllVariants(dslContext, treePath)
                .groupBy { it.get(ASSET_TREE.ID) }
                .values.mapNotNull { AssetAndVariants.from(it) }
        }
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
                            .orderBy(ASSET_TREE.ENTRY_ID.desc())
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

    private suspend fun fetchWithVariant(
        context: DSLContext,
        treePath: Ltree,
        entryId: Long?,
        transformation: Transformation,
    ): Record? {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: noCondition()
        val orderConditions =
            entryId?.let {
                arrayOf(ASSET_TREE.ENTRY_ID.desc(), ASSET_VARIANT.CREATED_AT.desc())
            } ?: arrayOf(ASSET_VARIANT.CREATED_AT.desc())

        return context.select()
            .from(ASSET_TREE)
            .leftJoin(ASSET_VARIANT)
            .on(calculateJoinVariantConditions(transformation))
            .where(ASSET_TREE.PATH.eq(treePath))
            .and(entryIdCondition)
            .orderBy(*orderConditions)
            .limit(1)
            .awaitFirstOrNull()
    }

    private suspend fun fetchAllWithVariant(
        context: DSLContext,
        treePath: Ltree,
        transformation: Transformation,
    ): List<Record> {
        return context.select()
            .from(ASSET_TREE)
            .leftJoin(ASSET_VARIANT)
            .on(calculateJoinVariantConditions(transformation))
            .where(ASSET_TREE.PATH.eq(treePath))
            .orderBy(ASSET_VARIANT.CREATED_AT.desc())
            .asFlow()
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

    private suspend fun fetchWithAllVariants(
        context: DSLContext,
        treePath: Ltree,
        entryId: Long?,
    ): List<Record> {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: ASSET_TREE.ENTRY_ID.eq(
                context.select(ASSET_TREE.ENTRY_ID)
                    .from(ASSET_TREE)
                    .where(ASSET_TREE.PATH.eq(treePath))
                    .orderBy(ASSET_TREE.ENTRY_ID.desc())
                    .limit(1),
            )

        return context.select()
            .from(ASSET_TREE)
            .join(ASSET_VARIANT).on(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
            .where(
                ASSET_TREE.PATH.eq(treePath),
            ).and(entryIdCondition)
            .orderBy(ASSET_VARIANT.CREATED_AT.desc())
            .asFlow()
            .toList()
    }

    private suspend fun fetchAllWithAllVariants(
        context: DSLContext,
        treePath: Ltree,
    ): List<Record> {
        return context.select()
            .from(ASSET_TREE)
            .join(ASSET_VARIANT).on(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
            .where(
                ASSET_TREE.PATH.eq(treePath),
            )
            .orderBy(ASSET_VARIANT.CREATED_AT.desc())
            .asFlow()
            .toList()
    }
}
