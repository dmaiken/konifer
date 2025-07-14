package asset.repository

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import asset.store.PersistResult
import asset.variant.VariantParameterGenerator
import image.model.ImageAttributes
import image.model.RequestedImageAttributes
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
import org.jooq.Record
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.condition
import org.jooq.impl.DSL.jsonbGetAttributeAsText
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.noCondition
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.jooq.postgres.extensions.types.Ltree
import org.jooq.postgres.extensions.types.Ltree.ltree
import tessa.jooq.indexes.ASSET_VARIANT_ATTRIBUTES_UQ
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
        val variantAttributes = variantParameterGenerator.generateImageVariantAttributes(asset.imageAttributes.toRequestedAttributes())
        return dslContext.transactionCoroutine { trx ->
            val entryId = getNextEntryId(trx.dsl(), asset.treePath)
            logger.info("Calculated entry_id: $entryId when storing new asset with path: ${asset.treePath}")
            val persistedAsset =
                trx.dsl().insertInto(ASSET_TREE)
                    .set(ASSET_TREE.ID, assetId)
                    .set(ASSET_TREE.PATH, ltree(asset.treePath))
                    .set(ASSET_TREE.ALT, asset.request.alt)
                    .set(ASSET_TREE.ENTRY_ID, entryId)
                    .set(ASSET_TREE.CREATED_AT, now)
                    .returning()
                    .awaitFirst()

            val persistedVariant =
                trx.dsl().insertInto(ASSET_VARIANT)
                    .set(ASSET_VARIANT.ID, UUID.randomUUID())
                    .set(ASSET_VARIANT.ASSET_ID, assetId)
                    .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, asset.persistResult.bucket)
                    .set(ASSET_VARIANT.OBJECT_STORE_KEY, asset.persistResult.key)
                    .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(variantAttributes.attributes))
                    .set(ASSET_VARIANT.ATTRIBUTES_KEY, variantAttributes.key)
                    .set(ASSET_VARIANT.ORIGINAL_VARIANT, true)
                    .set(ASSET_VARIANT.CREATED_AT, now)
                    .returning()
                    .awaitFirst()

            AssetAndVariants.from(persistedAsset, persistedVariant)
        }
    }

    override suspend fun storeVariant(
        treePath: String,
        entryId: Long,
        persistResult: PersistResult,
        imageAttributes: ImageAttributes,
    ): AssetAndVariants {
        return dslContext.transactionCoroutine { trx ->
            val asset =
                fetchWithVariant(
                    trx.dsl(),
                    treePath,
                    entryId,
                    RequestedImageAttributes.originalVariant(),
                )?.into(AssetTreeRecord::class.java)
            if (asset == null) {
                throw IllegalArgumentException("Asset with path: $treePath and entry id: $entryId not found in database")
            }
            val variantAttributes = variantParameterGenerator.generateImageVariantAttributes(imageAttributes.toRequestedAttributes())

            val persistedVariant =
                try {
                    trx.dsl().insertInto(ASSET_VARIANT)
                        .set(ASSET_VARIANT.ID, UUID.randomUUID())
                        .set(ASSET_VARIANT.ASSET_ID, asset.id)
                        .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, persistResult.bucket)
                        .set(ASSET_VARIANT.OBJECT_STORE_KEY, persistResult.key)
                        .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(variantAttributes.attributes))
                        .set(ASSET_VARIANT.ATTRIBUTES_KEY, variantAttributes.key)
                        .set(ASSET_VARIANT.ORIGINAL_VARIANT, false)
                        .set(ASSET_VARIANT.CREATED_AT, LocalDateTime.now())
                        .returning()
                        .awaitFirst()
                } catch (e: IntegrityConstraintViolationException) {
                    if (e.message?.contains(ASSET_VARIANT_ATTRIBUTES_UQ.name) == true) {
                        throw IllegalArgumentException(
                            "Variant already exists for asset with entry_id: $entryId at path: $treePath and attributes: $imageAttributes",
                        )
                    }
                    throw e
                }

            AssetAndVariants.from(asset, persistedVariant)
        }
    }

    override suspend fun fetchByPath(
        treePath: String,
        entryId: Long?,
        requestedImageAttributes: RequestedImageAttributes?,
    ): AssetAndVariants? {
        return if (requestedImageAttributes != null) {
            fetchWithVariant(dslContext, treePath, entryId, requestedImageAttributes)?.let { record ->
                AssetAndVariants.from(listOf(record))
            }
        } else {
            val result = fetchWithAllVariants(dslContext, treePath, entryId)
            AssetAndVariants.from(result)
        }
    }

    override suspend fun fetchAllByPath(treePath: String): List<AssetAndVariants> {
        return dslContext.select()
            .from(ASSET_TREE)
            .join(ASSET_VARIANT)
            .on(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
            .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
            .orderBy(ASSET_TREE.ENTRY_ID.desc(), ASSET_VARIANT.CREATED_AT.desc())
            .asFlow()
            .toList()
            .groupBy { it.get(ASSET_TREE.ID) }
            .values.mapNotNull { AssetAndVariants.from(it) }
    }

    override suspend fun deleteAssetByPath(
        treePath: String,
        entryId: Long?,
    ): List<VariantBucketAndKey> {
        val objectStoreInformation =
            dslContext.transactionCoroutine { trx ->
                val entryIdCondition =
                    entryId?.let {
                        ASSET_TREE.ENTRY_ID.eq(entryId)
                    } ?: ASSET_TREE.ENTRY_ID.eq(
                        trx.dsl().select(ASSET_TREE.ENTRY_ID)
                            .from(ASSET_TREE)
                            .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
                            .orderBy(ASSET_TREE.ENTRY_ID.desc())
                            .limit(1),
                    )
                val variantObjectStoreInformation =
                    trx.dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT).on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
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
                    .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
                    .and(entryIdCondition)
                    .awaitFirstOrNull()

                variantObjectStoreInformation
            }
        return objectStoreInformation
    }

    override suspend fun deleteAssetsByPath(
        treePath: String,
        recursive: Boolean,
    ) = coroutineScope {
        val deletedAssets =
            dslContext.transactionCoroutine { trx ->
                val objectStoreInformation =
                    trx.dsl()
                        .select(ASSET_VARIANT.OBJECT_STORE_BUCKET, ASSET_VARIANT.OBJECT_STORE_KEY)
                        .from(ASSET_TREE)
                        .join(ASSET_VARIANT).on(ASSET_TREE.ID.eq(ASSET_VARIANT.ASSET_ID))
                        .let {
                            if (recursive) {
                                it.where(ASSET_TREE.PATH.contains(Ltree.valueOf(treePath)))
                            } else {
                                it.where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
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
                            it.where(ASSET_TREE.PATH.contains(Ltree.valueOf(treePath)))
                        } else {
                            it.where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
                        }
                    }
                    .awaitFirstOrNull()
                objectStoreInformation
            }

        deletedAssets
    }

    private suspend fun getNextEntryId(
        context: DSLContext,
        treePath: String,
    ): Long {
        val maxField = max(ASSET_TREE.ENTRY_ID).`as`("max_entry")
        return context.select(maxField)
            .from(ASSET_TREE)
            .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
            .awaitFirstOrNull()
            ?.get(maxField)
            ?.inc() ?: 0L
    }

    private suspend fun fetchWithVariant(
        context: DSLContext,
        treePath: String,
        entryId: Long?,
        requestedImageAttributes: RequestedImageAttributes,
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
            .join(ASSET_VARIANT).on(calculateJoinVariantConditions(requestedImageAttributes))
            .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
            .and(entryIdCondition)
            .orderBy(*orderConditions)
            .limit(1)
            .awaitFirstOrNull()
    }

    private fun calculateJoinVariantConditions(requested: RequestedImageAttributes): Condition {
        var condition = ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID)
        if (requested.isOriginalVariant()) {
            return condition.and(ASSET_VARIANT.ORIGINAL_VARIANT).eq(true)
        }

        if (requested.width != null && requested.height != null) {
            val widthCondition = jsonbGetAttributeAsText(ASSET_VARIANT.ATTRIBUTES, "width").eq(requested.width.toString())
            val heightCondition = jsonbGetAttributeAsText(ASSET_VARIANT.ATTRIBUTES, "height").eq(requested.height.toString())

            condition =
                condition.and(
                    condition(widthCondition.or(heightCondition)),
                )
        } else if (requested.width != null) {
            condition = condition.and(jsonbGetAttributeAsText(ASSET_VARIANT.ATTRIBUTES, "width").eq(requested.width.toString()))
        } else if (requested.height != null) {
            condition = condition.and(jsonbGetAttributeAsText(ASSET_VARIANT.ATTRIBUTES, "height").eq(requested.height.toString()))
        }
        requested.mimeType?.let {
            condition = condition.and(jsonbGetAttributeAsText(ASSET_VARIANT.ATTRIBUTES, "mimeType").eq(it))
        }

        return condition
    }

    private suspend fun fetchWithAllVariants(
        context: DSLContext,
        treePath: String,
        entryId: Long?,
    ): List<Record> {
        val entryIdCondition =
            entryId?.let {
                ASSET_TREE.ENTRY_ID.eq(entryId)
            } ?: ASSET_TREE.ENTRY_ID.eq(
                context.select(ASSET_TREE.ENTRY_ID)
                    .from(ASSET_TREE)
                    .where(ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)))
                    .orderBy(ASSET_TREE.ENTRY_ID.desc())
                    .limit(1),
            )

        return context.select()
            .from(ASSET_TREE)
            .join(ASSET_VARIANT).on(ASSET_VARIANT.ASSET_ID.eq(ASSET_TREE.ID))
            .where(
                ASSET_TREE.PATH.eq(Ltree.valueOf(treePath)),
            ).and(entryIdCondition)
            .orderBy(ASSET_VARIANT.CREATED_AT.desc())
            .asFlow()
            .toList()
    }
}
