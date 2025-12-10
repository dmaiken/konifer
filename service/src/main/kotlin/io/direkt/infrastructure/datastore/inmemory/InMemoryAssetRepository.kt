package io.direkt.infrastructure.datastore.inmemory

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetId
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantBucketAndKey
import io.direkt.service.context.OrderBy
import io.ktor.util.logging.KtorSimpleLogger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class InMemoryAssetRepository : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = ConcurrentHashMap<String, MutableList<Asset>>()
    private val idReference = ConcurrentHashMap<AssetId, Asset>()

    override suspend fun storeNew(asset: Asset): Asset.PendingPersisted {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        val entryId = getNextEntryId(path)
        logger.info("Persisting asset at path: $path and entryId: $entryId")
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

    override suspend fun markReady(asset: Asset) {
        idReference[asset.id] = asset
        store[asset.path]?.removeIf { it.path == asset.path && it.entryId == asset.entryId }
        store[asset.path]?.add(asset)
    }

    override suspend fun markUploaded(variant: Variant) {
        val asset = idReference[variant.assetId] ?: return
        store[asset.path]
            ?.firstOrNull { it.entryId == asset.entryId }
            ?.let { asset ->
                asset.variants.removeIf { it.id == variant.id }
                asset.variants.add(variant)
            }
    }

    override suspend fun storeNewVariant(variant: Variant): Variant.Pending {
        if (variant !is Variant.Pending) {
            throw IllegalArgumentException("Variant must be pending")
        }
        val asset = idReference[variant.assetId]!!
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
        orderBy: OrderBy,
        labels: Map<String, String>,
    ): AssetData? {
        val asset = fetch(path, entryId, orderBy, labels) ?: return null
        val variants =
            if (transformation == null) {
                asset.variants
            } else {
                asset.variants
                    .firstOrNull { variant ->
                        transformation == variant.transformation
                    }?.let { matched ->
                        listOf(matched)
                    } ?: emptyList()
            }
        return asset.toAssetData()
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        limit: Int,
    ): List<AssetData> =
        store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]
            ?.toList()
            ?.filter { labels.all { entry -> it.labels[entry.key] == entry.value } }
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
                asset.toAssetData()
            }?.sortedWith(
                when (orderBy) {
                    OrderBy.CREATED -> compareByDescending<AssetData> { it.createdAt }
                    OrderBy.MODIFIED -> compareByDescending<AssetData> { it.modifiedAt }
                }.let {
                    it.thenByDescending { comparator -> comparator.entryId }
                },
            )?.take(limit) ?: emptyList()

    override suspend fun deleteAssetByPath(
        path: String,
        entryId: Long?,
    ): List<VariantBucketAndKey> {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath(path)
        logger.info("Deleting asset at path: $inMemoryPath and entryId: ${entryId ?: "not specified"}")

        val asset =
            store[inMemoryPath]?.let { assets ->
                val resolvedEntryId = entryId ?: assets.maxByOrNull { it.createdAt }?.entryId
                assets.firstOrNull { it.entryId == resolvedEntryId }
            }

        asset?.let {
            idReference.remove(it.id)
        }
        store[inMemoryPath]?.let { assets ->
            val resolvedEntryId = entryId ?: assets.maxByOrNull { it.createdAt }?.entryId
            resolvedEntryId?.let {
                assets.removeIf { it.entryId == resolvedEntryId }
            }
        }
        return asset?.variants?.map {
            VariantBucketAndKey(
                bucket = it.objectStoreBucket,
                key = it.objectStoreKey,
            )
        } ?: emptyList()
    }

    override suspend fun deleteAssetsByPath(
        path: String,
        recursive: Boolean,
    ): List<VariantBucketAndKey> {
        val inMemoryPath = InMemoryPathAdapter.toInMemoryPathFromUriPath(path)
        val objectStoreInformation = mutableListOf<VariantBucketAndKey>()
        if (recursive) {
            logger.info("Deleting assets (recursively) at path: $inMemoryPath")
            store.keys.filter { it.startsWith(inMemoryPath) }.forEach { path ->
                val assetAndVariants = store[path]
                assetAndVariants?.forEach {
                    objectStoreInformation.addAll(mapToBucketAndKey(it))
                }
                assetAndVariants?.map { it.id }?.forEach {
                    idReference.remove(it)
                }
                store.remove(path)
            }
        } else {
            logger.info("Deleting assets at path: $inMemoryPath")
            val assetAndVariants = store[inMemoryPath]
            assetAndVariants?.forEach {
                objectStoreInformation.addAll(mapToBucketAndKey(it))
            }
            assetAndVariants?.map { it.id }?.forEach {
                idReference.remove(it)
            }
            store.remove(inMemoryPath)
        }

        return objectStoreInformation
    }

    override suspend fun update(asset: Asset): Asset {
        if (asset !is Asset.Ready) {
            throw IllegalArgumentException("Asset must be in ready state")
        }
        fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, OrderBy.CREATED)
            ?: throw IllegalStateException("Asset does not exist")
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        store[path]?.removeIf { it.entryId == asset.entryId }
        store[path]?.add(asset)

        return asset
    }

    private fun mapToBucketAndKey(asset: Asset): List<VariantBucketAndKey> =
        asset.variants.map { variant ->
            VariantBucketAndKey(
                bucket = variant.objectStoreBucket,
                key = variant.objectStoreKey,
            )
        }

    private fun getNextEntryId(path: String): Long =
        store[path]
            ?.maxByOrNull { it.entryId!! }
            ?.entryId
            ?.inc() ?: 0

    private fun fetch(
        path: String,
        entryId: Long?,
        orderBy: OrderBy,
        labels: Map<String, String>,
    ): Asset? {
        val assets = store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)] ?: return null

        return assets
            .filter { asset ->
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
                when (orderBy) {
                    OrderBy.CREATED -> asset.createdAt
                    OrderBy.MODIFIED -> asset.modifiedAt
                }
            }
    }
}
