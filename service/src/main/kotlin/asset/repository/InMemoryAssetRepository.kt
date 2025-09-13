package asset.repository

import asset.Asset
import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import asset.variant.AssetVariant
import asset.variant.ImageVariantAttributes
import asset.variant.VariantParameterGenerator
import image.model.RequestedImageAttributes
import io.asset.handler.StoreAssetVariantDto
import io.asset.repository.InMemoryPathAdapter
import io.ktor.util.logging.KtorSimpleLogger
import java.time.LocalDateTime
import java.util.UUID

class InMemoryAssetRepository(
    private val variantParameterGenerator: VariantParameterGenerator,
) : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = mutableMapOf<String, MutableList<InMemoryAssetAndVariants>>()
    private val idReference = mutableMapOf<UUID, Asset>()

    override suspend fun store(asset: StoreAssetDto): AssetAndVariants {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(asset.path)
        val entryId = getNextEntryId(path)
        logger.info("Persisting asset at path: $path and entryId: $entryId")
        val key =
            variantParameterGenerator.generateImageVariantAttributes(asset.imageAttributes).second
        val assetAndVariants =
            AssetAndVariants(
                asset =
                    Asset(
                        id = UUID.randomUUID(),
                        alt = asset.request.alt,
                        entryId = entryId,
                        path = path,
                        createdAt = LocalDateTime.now(),
                    ),
                variants =
                    listOf(
                        AssetVariant(
                            objectStoreBucket = asset.persistResult.bucket,
                            objectStoreKey = asset.persistResult.key,
                            attributes =
                                ImageVariantAttributes(
                                    height = asset.imageAttributes.height,
                                    width = asset.imageAttributes.width,
                                    format = asset.imageAttributes.format,
                                ),
                            isOriginalVariant = true,
                            lqip = asset.lqips,
                            attributeKey = key,
                            createdAt = LocalDateTime.now(),
                        ),
                    ),
            )
        return assetAndVariants.also {
            store.computeIfAbsent(path) { mutableListOf() }.add(
                InMemoryAssetAndVariants(
                    asset = it.asset,
                    variants = it.variants.toMutableList(),
                ),
            )
            idReference.put(it.asset.id, it.asset)
        }
    }

    override suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants {
        val path = InMemoryPathAdapter.toInMemoryPathFromUriPath(variant.path)
        return store[path]?.let { assets ->
            val asset = assets.first { it.asset.entryId == variant.entryId }
            val key =
                variantParameterGenerator.generateImageVariantAttributes(variant.imageAttributes).second
            if (asset.variants.any { it.attributeKey == key }) {
                throw IllegalArgumentException(
                    "Variant already exists for asset with entry_id: ${variant.entryId} at path: $path " +
                        "with attributes: ${variant.imageAttributes}",
                )
            }
            val variant =
                AssetVariant(
                    objectStoreBucket = variant.persistResult.bucket,
                    objectStoreKey = variant.persistResult.key,
                    attributes =
                        ImageVariantAttributes(
                            height = variant.imageAttributes.height,
                            width = variant.imageAttributes.width,
                            format = variant.imageAttributes.format,
                        ),
                    isOriginalVariant = false,
                    lqip = variant.lqips,
                    attributeKey = key,
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
        requestedImageAttributes: RequestedImageAttributes?,
    ): AssetAndVariants? {
        return store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]?.let { assets ->
            val resolvedEntryId = entryId ?: assets.maxByOrNull { it.asset.createdAt }?.asset?.entryId
            assets.firstOrNull { it.asset.entryId == resolvedEntryId }?.let { assetAndVariants ->
                val variants =
                    if (requestedImageAttributes == null) {
                        assetAndVariants.variants
                    } else if (requestedImageAttributes.isOriginalVariant()) {
                        listOf(assetAndVariants.variants.first { it.isOriginalVariant })
                    } else {
                        assetAndVariants.variants.firstOrNull { variant ->
                            requestedImageAttributes.matchesImageAttributes(variant.attributes)
                        }?.let { matched ->
                            listOf(matched)
                        } ?: emptyList()
                    }
                AssetAndVariants(
                    asset = assetAndVariants.asset,
                    variants = variants,
                )
            }
        }
    }

    override suspend fun fetchAllByPath(
        path: String,
        requestedImageAttributes: RequestedImageAttributes?,
    ): List<AssetAndVariants> {
        return store[InMemoryPathAdapter.toInMemoryPathFromUriPath(path)]?.toList()?.sortedBy { it.asset.entryId }?.reversed()?.map {
                assetAndVariants ->
            val variants =
                if (requestedImageAttributes == null) {
                    assetAndVariants.variants
                } else if (requestedImageAttributes.isOriginalVariant()) {
                    listOf(assetAndVariants.variants.first { it.isOriginalVariant })
                } else {
                    assetAndVariants.variants.firstOrNull { variant ->
                        requestedImageAttributes.matchesImageAttributes(variant.attributes)
                    }?.let { matched ->
                        listOf(matched)
                    } ?: emptyList()
                }
            AssetAndVariants(
                asset = assetAndVariants.asset,
                variants = variants,
            )
        } ?: emptyList()
    }

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

    private fun mapToBucketAndKey(assetAndVariants: InMemoryAssetAndVariants): List<VariantBucketAndKey> {
        return assetAndVariants.variants.map { variant ->
            VariantBucketAndKey(
                bucket = variant.objectStoreBucket,
                key = variant.objectStoreKey,
            )
        }
    }

    private fun getNextEntryId(path: String): Long {
        return store[path]?.maxByOrNull { it.asset.entryId }?.asset?.entryId?.inc() ?: 0
    }

    private fun RequestedImageAttributes.matchesImageAttributes(attributes: ImageVariantAttributes): Boolean {
        if (width != null && height != null) {
            return attributes.width == width || attributes.height == height
        }
        if (width != null && attributes.width != width) {
            return false
        }
        if (height != null && attributes.height != height) {
            return false
        }
        if (format != null && attributes.format != format) {
            return false
        }
        return true
    }
}

private data class InMemoryAssetAndVariants(
    val asset: Asset,
    val variants: MutableList<AssetVariant>,
)
