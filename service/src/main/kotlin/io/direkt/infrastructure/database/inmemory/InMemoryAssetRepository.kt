package io.direkt.infrastructure.database.inmemory

import io.direkt.service.context.OrderBy
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.handler.dto.StoreAssetVariantDto
import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.asset.model.Asset
import io.direkt.asset.model.AssetAndVariants
import io.direkt.asset.model.VariantBucketAndKey
import io.direkt.asset.model.AssetVariant
import io.direkt.infrastructure.database.postgres.ImageVariantTransformation
import io.direkt.infrastructure.database.postgres.VariantParameterGenerator
import io.direkt.domain.image.Transformation
import io.direkt.domain.ports.AssetRepository
import io.ktor.util.logging.KtorSimpleLogger
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryAssetRepository : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = ConcurrentHashMap<String, MutableList<InMemoryAssetAndVariants>>()
    private val idReference = ConcurrentHashMap<UUID, Asset>()

    override suspend fun store(asset: StoreAssetDto): AssetAndVariants {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        val entryId = getNextEntryId(path)
        logger.info("Persisting asset at path: $path and entryId: $entryId")
        val key =
            VariantParameterGenerator.generateImageVariantTransformations(asset.attributes).second
        val now = LocalDateTime.now()
        val assetAndVariants =
            AssetAndVariants(
                asset =
                    Asset(
                        id = UUID.randomUUID(),
                        alt = asset.request.alt,
                        entryId = entryId,
                        path = path,
                        createdAt = now,
                        modifiedAt = now,
                        labels = asset.request.labels,
                        tags = asset.request.tags,
                        source = asset.source,
                        sourceUrl = asset.request.url,
                    ),
                variants =
                    listOf(
                        AssetVariant(
                            objectStoreBucket = asset.persistResult.bucket,
                            objectStoreKey = asset.persistResult.key,
                            attributes = asset.attributes,
                            transformation = ImageVariantTransformation.originalTransformation(asset.attributes).toTransformation(),
                            isOriginalVariant = true,
                            lqip = asset.lqips,
                            transformationKey = key,
                            createdAt = now,
                        ),
                    ),
            )
        return assetAndVariants.also {
            store.computeIfAbsent(path) { Collections.synchronizedList(mutableListOf()) }.add(
                InMemoryAssetAndVariants(
                    asset = it.asset,
                    variants = it.variants.toMutableList(),
                ),
            )
            idReference[it.asset.id] = it.asset
        }
    }

    override suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(variant.path)
        return store[path]?.let { assets ->
            val asset = assets.first { it.asset.entryId == variant.entryId }
            val key =
                VariantParameterGenerator.generateImageVariantTransformations(variant.transformation).second
            if (asset.variants.any { it.transformationKey == key }) {
                throw IllegalArgumentException(
                    "Variant already exists for asset with entry_id: ${variant.entryId} at path: $path " +
                        "with attributes: ${variant.attributes}, transformation: ${variant.transformation}",
                )
            }
            val variant =
                AssetVariant(
                    objectStoreBucket = variant.persistResult.bucket,
                    objectStoreKey = variant.persistResult.key,
                    attributes = variant.attributes,
                    transformation = variant.transformation,
                    isOriginalVariant = false,
                    lqip = variant.lqips,
                    transformationKey = key,
                    createdAt = LocalDateTime.now(),
                )
            asset.variants.add(variant)
            asset.variants.sortByDescending { it.createdAt }

            AssetAndVariants(
                asset = asset.asset,
                variants = listOf(variant),
            )
        } ?: throw IllegalArgumentException("Asset with path: $path and entry id: ${variant.entryId} not found in database")
    }

    override suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
    ): AssetAndVariants? {
        val assetAndVariants = fetch(path, entryId, orderBy, labels) ?: return null
        val variants =
            if (transformation == null) {
                assetAndVariants.variants
            } else if (transformation.originalVariant) {
                listOf(assetAndVariants.variants.first { it.isOriginalVariant })
            } else {
                assetAndVariants.variants
                    .firstOrNull { variant ->
                        transformation == variant.transformation
                    }?.let { matched ->
                        listOf(matched)
                    } ?: emptyList()
            }
        return AssetAndVariants(
            asset = assetAndVariants.asset,
            variants = variants,
        )
    }

    override suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy,
        labels: Map<String, String>,
        limit: Int,
    ): List<AssetAndVariants> =
        store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]
            ?.toList()
            ?.filter { labels.all { entry -> it.asset.labels[entry.key] == entry.value } }
            ?.map { assetAndVariants ->
                val variants =
                    if (transformation == null) {
                        assetAndVariants.variants
                    } else if (transformation.originalVariant) {
                        listOf(assetAndVariants.variants.first { it.isOriginalVariant })
                    } else {
                        assetAndVariants.variants
                            .firstOrNull { variant ->
                                transformation == variant.transformation
                            }?.let { matched ->
                                listOf(matched)
                            } ?: emptyList()
                    }
                AssetAndVariants(
                    asset = assetAndVariants.asset,
                    variants = variants,
                )
            }?.sortedWith(
                when (orderBy) {
                    OrderBy.CREATED -> compareByDescending<AssetAndVariants> { it.asset.createdAt }
                    OrderBy.MODIFIED -> compareByDescending<AssetAndVariants> { it.asset.modifiedAt }
                }.let {
                    it.thenByDescending { comparator -> comparator.asset.entryId }
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
                val resolvedEntryId = entryId ?: assets.maxByOrNull { it.asset.createdAt }?.asset?.entryId
                assets.firstOrNull { it.asset.entryId == resolvedEntryId }
            }

        asset?.let {
            idReference.remove(it.asset.id)
        }
        store[inMemoryPath]?.let { assets ->
            val resolvedEntryId = entryId ?: assets.maxByOrNull { it.asset.createdAt }?.asset?.entryId
            resolvedEntryId?.let {
                assets.removeIf { it.asset.entryId == resolvedEntryId }
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
                assetAndVariants?.map { it.asset.id }?.forEach {
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
            assetAndVariants?.map { it.asset.id }?.forEach {
                idReference.remove(it)
            }
            store.remove(inMemoryPath)
        }

        return objectStoreInformation
    }

    override suspend fun update(asset: UpdateAssetDto): AssetAndVariants {
        val fetched =
            fetchByPath(asset.path, asset.entryId, Transformation.ORIGINAL_VARIANT, OrderBy.CREATED)
                ?: throw IllegalStateException("Asset does not exist")

        val isModified =
            asset.request.alt != fetched.asset.alt ||
                asset.request.labels != fetched.asset.labels ||
                asset.request.tags != asset.request.tags
        val updated =
            fetched.copy(
                asset =
                    fetched.asset.copy(
                        alt = asset.request.alt,
                        labels = asset.request.labels,
                        tags = asset.request.tags,
                        modifiedAt = if (isModified) LocalDateTime.now() else fetched.asset.modifiedAt,
                    ),
            )
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        store[path]?.removeIf { it.asset.entryId == asset.entryId }
        store[path]?.add(
            InMemoryAssetAndVariants(
                asset = updated.asset,
                variants = updated.variants.toMutableList(),
            ),
        )

        return updated
    }

    private fun mapToBucketAndKey(assetAndVariants: InMemoryAssetAndVariants): List<VariantBucketAndKey> =
        assetAndVariants.variants.map { variant ->
            VariantBucketAndKey(
                bucket = variant.objectStoreBucket,
                key = variant.objectStoreKey,
            )
        }

    private fun getNextEntryId(path: String): Long =
        store[path]
            ?.maxByOrNull { it.asset.entryId }
            ?.asset
            ?.entryId
            ?.inc() ?: 0

    private fun fetch(
        path: String,
        entryId: Long?,
        orderBy: OrderBy,
        labels: Map<String, String>,
    ): InMemoryAssetAndVariants? {
        val assets = store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)] ?: return null

        return assets
            .filter { asset ->
                if (entryId != null) {
                    asset.asset.entryId == entryId
                } else {
                    true
                }
            }.filter { asset ->
                if (labels.isNotEmpty()) {
                    labels.all { asset.asset.labels[it.key] == it.value }
                } else {
                    true
                }
            }.maxByOrNull { asset ->
                when (orderBy) {
                    OrderBy.CREATED -> asset.asset.createdAt
                    OrderBy.MODIFIED -> asset.asset.modifiedAt
                }
            }
    }
}

private data class InMemoryAssetAndVariants(
    val asset: Asset,
    val variants: MutableList<AssetVariant>,
)
