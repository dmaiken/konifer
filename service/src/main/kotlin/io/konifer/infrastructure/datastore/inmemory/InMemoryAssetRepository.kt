package io.konifer.infrastructure.datastore.inmemory

import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.DeleteAssetsCommand
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantAlreadyExistsException
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

internal data class ObjectStoreReference(
    val bucket: String,
    val key: String,
)

class InMemoryAssetRepository : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = ConcurrentHashMap<String, MutableList<Asset>>()
    private val idReference = ConcurrentHashMap<AssetId, Asset>()
    private val storeMutex = Mutex()

    init {
        logger.warn("The in-memory data store provider is enabled. This should NOT be used in production!")
    }

    override suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted {
        storeMutex.withLock {
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
    }

    override suspend fun markReady(asset: Asset.Ready) {
        storeMutex.withLock {
            idReference[asset.id] = asset
            store[asset.path]?.removeIf { it.path == asset.path && it.entryId == asset.entryId }
            store[asset.path]?.add(asset)
        }
    }

    override suspend fun markUploaded(variant: Variant.Ready) {
        storeMutex.withLock {
            val asset = idReference[variant.assetId] ?: return
            val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
            store[path]
                ?.firstOrNull { it.entryId == asset.entryId }
                ?.let { asset ->
                    asset.variants.removeIf { it.id == variant.id }
                    asset.variants.add(variant)
                }
        }
    }

    override suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending {
        storeMutex.withLock {
            val asset = idReference[variant.assetId] ?: throw IllegalArgumentException("Asset not found")
            val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
            return store[path]?.let { assets ->
                val asset = assets.first { it.entryId == asset.entryId }
                if (asset.variants.any { it.transformation == variant.transformation }) {
                    throw VariantAlreadyExistsException("Variant already exists for asset: ${asset.id.value}")
                }
                asset.variants.add(variant)
                asset.variants.sortByDescending { it.createdAt }

                variant
            } ?: throw IllegalArgumentException("Asset with path: $path and entry id: ${asset.entryId} not found in database")
        }
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
        val now = LocalDateTime.now(UTC)
        val asset = fetch(path, entryId, order, labels, includeOnlyReady) ?: return null
        val variants =
            when {
                transformation == null -> asset.variants
                transformation.originalVariant -> asset.variants.filter { it.isOriginalVariant }
                else ->
                    asset.variants
                        .firstOrNull { variant ->
                            transformation == variant.transformation
                        }?.let { matched ->
                            listOf(matched)
                        } ?: emptyList()
            }.filter {
                (it.expiresAt == null || it.expiresAt!! > now) && it.uploadedAt != null
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
        deleteAndReturnObjectReferences(
            DeleteAssetsCommand.Entry(
                path = path,
                entryId = entryId,
            ),
        )
    }

    override suspend fun deleteAllByPath(
        path: String,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ) {
        deleteAndReturnObjectReferences(
            DeleteAssetsCommand.AtPath(
                path = path,
                labels = labels,
                order = order,
                limit = limit,
            ),
        )
    }

    override suspend fun deleteRecursivelyByPath(
        path: String,
        labels: Map<String, String>,
    ) {
        deleteAndReturnObjectReferences(
            DeleteAssetsCommand.Recursively(
                path = path,
                labels = labels,
            ),
        )
    }

    override suspend fun deleteByAssetId(assetId: AssetId) {
        logger.info("Deleting asset with id: : $assetId")

        storeMutex.withLock {
            idReference[assetId]?.let { asset ->
                removeAssets(listOf(asset))
            }
        }
    }

    internal suspend fun deleteAndReturnObjectReferences(command: DeleteAssetsCommand): List<ObjectStoreReference> =
        storeMutex.withLock {
            val assets =
                when (command) {
                    is DeleteAssetsCommand.Entry -> {
                        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(command.path)
                        logger.info("Deleting asset at path: $path, entryId: ${command.entryId}")
                        store[path]
                            ?.firstOrNull { it.entryId == command.entryId }
                            ?.let(::listOf)
                            ?: emptyList()
                    }
                    is DeleteAssetsCommand.AtPath -> {
                        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(command.path)
                        logger.info(
                            "Deleting assets at path: $path, labels: ${command.labels}, " +
                                "orderBy: ${command.order}, limit: ${command.limit}",
                        )
                        selectAssetsAtPath(
                            path = path,
                            labels = command.labels,
                            order = command.order,
                            limit = command.limit,
                        )
                    }
                    is DeleteAssetsCommand.Recursively -> {
                        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(command.path)
                        logger.info("Deleting assets (recursively) at path: $path with labels: ${command.labels}")
                        store.keys
                            .filter { it.startsWith(path) }
                            .flatMap { storedPath ->
                                store[storedPath]
                                    ?.filter { asset ->
                                        command.labels.all { entry -> asset.labels.asMap()[entry.key] == entry.value }
                                    }.orEmpty()
                            }
                    }
                }

            val objectReferences =
                assets
                    .flatMap { it.variants }
                    .map { ObjectStoreReference(bucket = it.objectStoreBucket, key = it.objectStoreKey) }
                    .distinct()

            removeAssets(assets)
            objectReferences
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

    private fun selectAssetsAtPath(
        path: String,
        labels: Map<String, String>,
        order: Order,
        limit: Int,
    ): List<Asset> =
        store[path]
            ?.asSequence()
            ?.filter { asset -> labels.all { entry -> asset.labels.asMap()[entry.key] == entry.value } }
            ?.sortedWith(
                when (order) {
                    Order.NEW -> compareByDescending<Asset> { it.createdAt }
                    Order.MODIFIED -> compareByDescending { it.modifiedAt }
                }.thenByDescending { it.entryId },
            )?.let { assets ->
                if (limit > 0) assets.take(limit) else assets
            }?.toList()
            ?: emptyList()

    private fun removeAssets(assets: List<Asset>) {
        val ids = assets.mapTo(mutableSetOf()) { it.id }
        ids.forEach(idReference::remove)
        assets
            .map { InMemoryPathAdapter.toInMemoryPathFromUriPath(it.path) }
            .distinct()
            .forEach { path ->
                store[path]?.removeIf { it.id in ids }
            }
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
                    labels.all { asset.labels.asMap()[it.key] == it.value }
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
    ): List<AssetData> {
        val now = LocalDateTime.now(UTC)
        return store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]
            ?.asSequence()
            ?.filter {
                if (includeOnlyReady) {
                    it.isReady
                } else {
                    true
                }
            }?.filter { labels.all { entry -> it.labels.asMap()[entry.key] == entry.value } }
            ?.map { asset ->
                val variants =
                    if (transformation == null) {
                        asset.variants
                    } else if (transformation.originalVariant) {
                        listOf(asset.variants.first { it.isOriginalVariant })
                    } else {
                        asset.variants
                            .firstOrNull { variant ->
                                (variant.expiresAt == null || variant.expiresAt!! > now) && transformation == variant.transformation
                            }?.let { matched ->
                                listOf(matched)
                            } ?: emptyList()
                    }
                asset.toAssetData(variants)
            }?.sortedWith(
                when (order) {
                    Order.NEW -> compareByDescending<AssetData> { it.createdAt }
                    Order.MODIFIED -> compareByDescending { it.modifiedAt }
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
}
