package io.konifer.infrastructure.datastore.inmemory

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.service.context.selector.Order
import io.ktor.util.logging.KtorSimpleLogger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class InMemoryAssetRepository : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = ConcurrentHashMap<String, MutableList<Asset>>()
    private val idReference = ConcurrentHashMap<AssetId, Asset>()

    override suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        val entryId = getNextEntryId(path)
        logger.info("Persisting asset at path: $path, entryId: $entryId")
        return Asset
            .PendingPersisted(
                id = asset.id,
                path = asset.path,
                entryId = entryId,
                alt = asset.alt,
                labels = asset.labels,
                tags = asset.tags,
                source = asset.source,
                sourceUrl = asset.sourceUrl,
                createdAt = asset.createdAt,
                modifiedAt = asset.modifiedAt,
                isReady = false,
                variants = asset.variants,
            ).also {
                store.computeIfAbsent(path) { Collections.synchronizedList(mutableListOf()) }.add(it)
                idReference[it.id] = it
            }
    }

    override suspend fun markReady(asset: Asset.Ready) {
        idReference[asset.id] = asset
        store[asset.path]?.removeIf { it.path == asset.path && it.entryId == asset.entryId }
        store[asset.path]?.add(asset)
    }

    override suspend fun markUploaded(variant: Variant.Ready) {
        val asset = idReference[variant.assetId] ?: return
        store[asset.path]
            ?.firstOrNull { it.entryId == asset.entryId }
            ?.let { asset ->
                asset.variants.removeIf { it.id == variant.id }
                asset.variants.add(variant)
            }
    }

    override suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending {
        val asset = idReference[variant.assetId] ?: throw IllegalArgumentException("Asset not found")
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        return store[path]?.let { assets ->
            val asset = assets.first { it.entryId == asset.entryId }
            if (asset.variants.any { it.transformation == variant.transformation }) {
                throw IllegalArgumentException(
                    "Variant already exists for asset with entry_id: ${asset.entryId} at path: $path " +
                        "with attributes: ${variant.attributes}, transformation: ${variant.transformation}",
                )
            }
            asset.variants.add(variant)
            asset.variants.sortByDescending { it.createdAt }

            variant
        } ?: throw IllegalArgumentException("Asset with path: $path and entry id: ${asset.entryId} not found in database")
    }

    override suspend fun fetchForUpdate(
        path: String,
        entryId: Long,
    ): Asset? =
        InMemoryPathAdapter.toInMemoryPathFromUriPath(path).let {
            store[it]?.firstOrNull { asset -> asset.entryId == entryId }
        }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        order: Order,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
    ): AssetData? {
        val asset = fetch(path, entryId, order, labels, includeOnlyReady) ?: return null
        val variants =
            if (transformation == null) {
                asset.variants
            } else if (transformation.originalVariant) {
                asset.variants.filter { it.isOriginalVariant }
            } else {
                asset.variants
                    .firstOrNull { variant ->
                        transformation == variant.transformation
                    }?.let { matched ->
                        listOf(matched)
                    } ?: emptyList()
            }
        return asset.toAssetData(variants)
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ): List<AssetData> =
        fetchAll(
            path = path,
            transformation = transformation,
            order = order,
            labels = labels,
            limit = limit,
            includeOnlyReady = true,
        )

    override suspend fun deleteByPath(
        path: String,
        entryId: Long,
    ) {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath(path)
        logger.info("Deleting asset at path: $inMemoryPath, entryId: $entryId")

        val asset =
            store[inMemoryPath]?.let { assets ->
                assets.firstOrNull { it.entryId == entryId }
            }

        asset?.let {
            idReference.remove(it.id)
        }
        store[inMemoryPath]?.let { assets ->
            assets.removeIf { it.entryId == entryId }
        }
    }

    override suspend fun deleteAllByPath(
        path: String,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ) {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath(path)
        logger.info("Deleting assets at path: $inMemoryPath, labels: $labels, orderBy: $order, limit: $limit")
        val assetsToDelete =
            fetchAll(
                path = path,
                order = order,
                transformation = null,
                limit = limit,
                labels = labels,
                includeOnlyReady = false,
            )
        assetsToDelete.map { it.id }.forEach {
            idReference.remove(it)
        }
        store[inMemoryPath]?.let { assets ->
            assets.removeIf { asset -> asset.id in assetsToDelete.map { it.id } }
        }
    }

    override suspend fun deleteRecursivelyByPath(
        path: String,
        labels: Map<String, String>,
    ) {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath(path)
        logger.info("Deleting assets (recursively) at path: $inMemoryPath with labels: $labels")
        store.keys.filter { it.startsWith(inMemoryPath) }.forEach { path ->
            val assetAndVariants = store[path] ?: emptyList()
            val assetsToDelete = assetAndVariants.filter { labels.all { entry -> it.labels[entry.key] == entry.value } }
            assetsToDelete.map { it.id }.forEach {
                idReference.remove(it)
            }
            store[path]?.let { assets ->
                assets.removeIf { asset -> asset.id in assetsToDelete.map { it.id } }
            }
        }
    }

    override suspend fun deleteByAssetId(assetId: AssetId) {
        logger.info("Deleting asset with id: : $assetId")

        idReference[assetId]?.let { asset ->
            idReference.remove(asset.id)
            val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
            store[path]?.let { assets ->
                assets.removeIf { it.id == assetId }
            }
        }
    }

    override suspend fun update(asset: Asset): Asset {
        if (asset !is Asset.Ready) {
            throw IllegalArgumentException("Asset must be in ready state")
        }
        fetch(asset.path, asset.entryId, Order.NEW, emptyMap(), true)
            ?: throw IllegalStateException("Asset does not exist")
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        store[path]?.removeIf { it.entryId == asset.entryId }
        store[path]?.add(asset)

        return asset
    }

    private fun getNextEntryId(path: String): Long =
        store[path]
            ?.maxByOrNull { it.entryId!! }
            ?.entryId
            ?.inc() ?: 0

    private fun fetch(
        path: String,
        entryId: Long?,
        order: Order,
        labels: Map<String, String>,
        includeOnlyReady: Boolean,
    ): Asset? {
        val assets = store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)] ?: return null

        return assets
            .asSequence()
            .filter {
                if (includeOnlyReady) {
                    it.isReady
                } else {
                    true
                }
            }.filter { asset ->
                if (entryId != null) {
                    asset.entryId == entryId
                } else {
                    true
                }
            }.filter { asset ->
                if (labels.isNotEmpty()) {
                    labels.all { asset.labels[it.key] == it.value }
                } else {
                    true
                }
            }.maxByOrNull { asset ->
                when (order) {
                    Order.NEW -> asset.createdAt
                    Order.MODIFIED -> asset.modifiedAt
                }
            }
    }

    private fun fetchAll(
        path: String,
        transformation: Transformation?,
        order: Order,
        labels: Map<String, String>,
        limit: Int,
        includeOnlyReady: Boolean,
    ): List<AssetData> =
        store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]
            ?.asSequence()
            ?.filter {
                if (includeOnlyReady) {
                    it.isReady
                } else {
                    true
                }
            }?.filter { labels.all { entry -> it.labels[entry.key] == entry.value } }
            ?.map { asset ->
                val variants =
                    if (transformation == null) {
                        asset.variants
                    } else if (transformation.originalVariant) {
                        listOf(asset.variants.first { it.isOriginalVariant })
                    } else {
                        asset.variants
                            .firstOrNull { variant ->
                                transformation == variant.transformation
                            }?.let { matched ->
                                listOf(matched)
                            } ?: emptyList()
                    }
                asset.toAssetData(variants)
            }?.sortedWith(
                when (order) {
                    Order.NEW -> compareByDescending<AssetData> { it.createdAt }
                    Order.MODIFIED -> compareByDescending<AssetData> { it.modifiedAt }
                }.let {
                    it.thenByDescending { comparator -> comparator.entryId }
                },
            )?.let {
                if (limit > 0) {
                    it.take(limit)
                } else {
                    it
                }
            }?.toList() ?: emptyList()
}
