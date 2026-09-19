package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.asset.toAssetTags
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantAlreadyExistsException
import io.konifer.infrastructure.datastore.postgres.DeleteAssetHelper.deleteAssets
import io.konifer.infrastructure.datastore.postgres.statement.DeleteStatementGenerator
import io.konifer.infrastructure.datastore.postgres.statement.InsertStatementGenerator
import io.konifer.infrastructure.datastore.postgres.statement.SelectForUpdateStatementGenerator
import io.konifer.infrastructure.datastore.postgres.statement.SelectStatementGenerator
import io.konifer.infrastructure.datastore.postgres.statement.UpdateStatementGenerator
import io.ktor.util.logging.KtorSimpleLogger
import konifer.jooq.indexes.ASSET_VARIANT_TRANSFORMATION_UQ
import konifer.jooq.keys.ASSET_VARIANT__FK_ASSET_VARIANT_ASSET_ID_ASSET_TREE_ID
import konifer.jooq.tables.references.ASSET_TREE
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class PostgresAssetRepository(
    private val dslContext: DSLContext,
) : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    override suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted {
        val now = LocalDateTime.now(UTC)
        return dslContext.contextualizedTransactionCoroutine {
            val insert =
                InsertStatementGenerator.insertAsset(
                    asset = asset,
                    createdAt = now,
                )
            val persistedAsset = insert.awaitFirst()
            InsertStatementGenerator
                .insertLabels(
                    assetId = asset.id,
                    labels = asset.labels,
                    dateTime = now,
                )?.awaitSingle()
            InsertStatementGenerator
                .insertTags(
                    assetId = asset.id,
                    tags = asset.tags,
                    dateTime = now,
                )?.awaitSingle()

            val persistedVariant =
                InsertStatementGenerator
                    .insertVariant(variant = asset.variants.first())
                    .awaitFirst()

            logger.info("Assigned entry_id: ${persistedAsset.entryId} to new asset with path: ${persistedAsset.path?.toString()}")
            persistedAsset.toPendingPersisted(persistedVariant, asset.labels, asset.tags)
        }
    }

    override suspend fun markReady(asset: Asset.Ready) {
        val originalVariant = asset.variants.first { it.isOriginalVariant }
        dslContext.contextualizedTransactionCoroutine {
            UpdateStatementGenerator
                .updateAssetReady(asset)
                .awaitFirst()

            UpdateStatementGenerator
                .updateVariantUploaded(originalVariant)
                .awaitFirst()
        }
    }

    override suspend fun markUploaded(variant: Variant.Ready) {
        with(dslContext) {
            UpdateStatementGenerator
                .updateVariantUploaded(variant)
                .awaitFirst()
        }
    }

    override suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending =
        dslContext.contextualizedTransactionCoroutine {
            val persistedVariant =
                try {
                    InsertStatementGenerator
                        .insertVariant(
                            variant = variant,
                        ).awaitFirst()
                } catch (e: IntegrityConstraintViolationException) {
                    if (e.message?.contains(ASSET_VARIANT_TRANSFORMATION_UQ.name) == true) {
                        throw VariantAlreadyExistsException("Variant already exists for assetId: ${variant.assetId.value}")
                    }
                    if (e.message?.contains(ASSET_VARIANT__FK_ASSET_VARIANT_ASSET_ID_ASSET_TREE_ID.name) == true) {
                        throw IllegalArgumentException("No asset exists for assetId: ${variant.assetId.value}")
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
            path = path,
            entryId = entryId,
            transformation = null,
            order = Order.NEW,
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
                    labels = fetched.labels.associate { Pair(checkNotNull(it.labelKey), checkNotNull(it.labelValue)) }.toAssetLabels(),
                    tags =
                        fetched.tags
                            .mapNotNull { it.tagValue }
                            .toSet()
                            .toAssetTags(),
                )
            }
        }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        order: Order,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
    ): AssetData? =
        fetch(
            context = dslContext,
            path = path,
            entryId = entryId,
            transformation = transformation,
            order = order,
            labels = labels,
            includeOnlyReady = includeOnlyReady,
        )?.let {
            it.asset.toAssetData(it.variants, it.labels, it.tags)
        }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ): List<AssetData> {
        val handles =
            with(dslContext) {
                SelectStatementGenerator.fetch(
                    path = path,
                    entryId = null,
                    transformation = transformation,
                    order = order,
                    labels = labels,
                    includeOnlyReady = true,
                    limit = limit,
                )
            }
        return handles.statement
            .asFlow()
            .map { record ->
                val asset = record.into(ASSET_TREE)
                val variants = record.getValue(handles.variants)
                val labels = record.getValue(handles.labels)
                val tags = record.getValue(handles.tags)

                AssetRecordsDto(asset, variants, labels, tags)
            }.map { it.asset.toAssetData(it.variants, it.labels, it.tags) }
            .toList()
    }

    override suspend fun deleteByPath(
        path: String,
        entryId: Long,
    ) {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        deleteAssets(dslContext) {
            SelectForUpdateStatementGenerator.assetByPath(treePath, entryId)
        }
    }

    override suspend fun deleteAllByPath(
        path: String,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ) {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)

        val count =
            deleteAssets(dslContext) {
                SelectForUpdateStatementGenerator.assetsByPath(treePath, labels, order, limit)
            }

        logger.info("Deleted $count assets in path: $path")
    }

    override suspend fun deleteRecursivelyByPath(
        path: String,
        labels: Map<String, String>,
    ) {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(path)
        deleteAssets(dslContext) {
            SelectForUpdateStatementGenerator.assetsRecursivelyByPath(treePath, labels)
        }
    }

    override suspend fun deleteByAssetId(assetId: AssetId) {
        deleteAssets(dslContext) {
            SelectForUpdateStatementGenerator.assetById(assetId)
        }
    }

    override suspend fun update(asset: Asset.Ready): Asset {
        val fetched =
            fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, Order.NEW)
                ?: throw IllegalStateException("Asset not found with path: ${asset.path}, entryId: ${asset.entryId}")

        val assetId = fetched.id
        var modified = false
        dslContext.contextualizedTransactionCoroutine {
            if (fetched.labels != asset.labels) {
                modified = true
                DeleteStatementGenerator
                    .deleteLabels(assetId)
                    .awaitFirst()
                InsertStatementGenerator
                    .insertLabels(
                        assetId = asset.id,
                        labels = asset.labels,
                    )?.awaitSingle()
            }
            if (fetched.tags != asset.tags) {
                modified = true
                DeleteStatementGenerator
                    .deleteTags(assetId)
                    .awaitFirst()
                InsertStatementGenerator
                    .insertTags(
                        assetId = assetId,
                        tags = asset.tags,
                    )?.awaitSingle()
            }
            if (fetched.alt != asset.alt?.value) {
                modified = true
                UpdateStatementGenerator
                    .updateAsset(asset)
                    .awaitFirst()
            }
            if (modified) {
                UpdateStatementGenerator
                    .updateModifiedAt(asset)
                    .awaitFirst()
            }
        }

        return if (modified) {
            fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, Order.NEW)
                ?.let { Asset.Ready.from(it) }
                ?: throw IllegalStateException("Asset does not exist after updating")
        } else {
            asset
        }
    }

    private suspend fun fetch(
        context: DSLContext,
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        order: Order,
        labels: Map<String, String> = emptyMap(),
        includeOnlyReady: Boolean = true,
    ): AssetRecordsDto? {
        return with(context) {
            SelectStatementGenerator.fetch(
                path = path,
                entryId = entryId,
                transformation = transformation,
                order = order,
                labels = labels,
                includeOnlyReady = includeOnlyReady,
                limit = 1,
            )
        }.let { handles ->
            val record = handles.statement.awaitFirstOrNull() ?: return null
            val asset = record.into(ASSET_TREE)
            val variants = record.getValue(handles.variants)
            val labels = record.getValue(handles.labels)
            val tags = record.getValue(handles.tags)

            AssetRecordsDto(asset, variants, labels, tags)
        }
    }
}
