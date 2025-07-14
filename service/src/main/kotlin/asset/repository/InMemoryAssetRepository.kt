package asset.repository

import asset.Asset
import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import asset.store.PersistResult
import asset.variant.AssetVariant
import asset.variant.ImageVariantAttributes
import asset.variant.VariantParameterGenerator
import image.model.ImageAttributes
import image.model.RequestedImageAttributes
import io.ktor.util.logging.KtorSimpleLogger
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class InMemoryAssetRepository(
    private val variantParameterGenerator: VariantParameterGenerator,
) : AssetRepository {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private val store = mutableMapOf<String, MutableList<InMemoryAssetAndVariants>>()
    private val idReference = mutableMapOf<UUID, Asset>()

    override suspend fun store(asset: StoreAssetDto): AssetAndVariants {
        val entryId = getNextEntryId(asset.treePath)
        logger.info("Persisting asset at path: ${asset.treePath} and entryId: $entryId")
        val key =
            variantParameterGenerator.generateImageVariantAttributes(asset.imageAttributes).key.let {
                Base64.getEncoder().encodeToString(it)
            }
        val assetAndVariants =
            AssetAndVariants(
                asset =
                    Asset(
                        id = UUID.randomUUID(),
                        alt = asset.request.alt,
                        entryId = entryId,
                        path = asset.treePath,
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
                                    mimeType = asset.imageAttributes.mimeType,
                                ),
                            isOriginalVariant = true,
                            attributeKey = key,
                            createdAt = LocalDateTime.now(),
                        ),
                    ),
            )
        return assetAndVariants.also {
            store.computeIfAbsent(asset.treePath) { mutableListOf() }.add(
                InMemoryAssetAndVariants(
                    asset = it.asset,
                    variants = it.variants.toMutableList(),
                ),
            )
            idReference.put(it.asset.id, it.asset)
        }
    }

    override suspend fun storeVariant(
        treePath: String,
        entryId: Long,
        persistResult: PersistResult,
        imageAttributes: ImageAttributes,
    ): AssetAndVariants {
        return store[treePath]?.let { assets ->
            val asset = assets.first { it.asset.entryId == entryId }
            val key =
                variantParameterGenerator.generateImageVariantAttributes(imageAttributes).key.let {
                    Base64.getEncoder().encodeToString(it)
                }
            if (asset.variants.any { it.attributeKey.contentEquals(key) }) {
                throw IllegalArgumentException(
                    "Variant already exists for asset with entry_id: $entryId at path: $treePath with attributes: $imageAttributes",
                )
            }
            val variant =
                AssetVariant(
                    objectStoreBucket = persistResult.bucket,
                    objectStoreKey = persistResult.key,
                    attributes =
                        ImageVariantAttributes(
                            height = imageAttributes.height,
                            width = imageAttributes.width,
                            mimeType = imageAttributes.mimeType,
                        ),
                    isOriginalVariant = false,
                    attributeKey = key,
                    createdAt = LocalDateTime.now(),
                )
            asset.variants.add(variant)
            asset.variants.sortByDescending { it.createdAt }

            AssetAndVariants(
                asset = asset.asset,
                variants = listOf(variant),
            )
        } ?: throw IllegalArgumentException("Asset with path: $treePath and entry id: $entryId not found in database")
    }

    override suspend fun fetchByPath(
        treePath: String,
        entryId: Long?,
        requestedImageAttributes: RequestedImageAttributes?,
    ): AssetAndVariants? {
        return store[treePath]?.let { assets ->
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
                            logger.info("Found the variant")
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

    override suspend fun fetchAllByPath(treePath: String): List<AssetAndVariants> {
        return store[treePath]?.toList()?.sortedBy { it.asset.entryId }?.reversed()?.map {
            AssetAndVariants(
                asset = it.asset,
                variants = it.variants,
            )
        } ?: emptyList()
    }

    override suspend fun deleteAssetByPath(
        treePath: String,
        entryId: Long?,
    ): List<VariantBucketAndKey> {
        logger.info("Deleting asset at path: $treePath and entryId: ${entryId ?: "not specified"}")

        val asset =
            store[treePath]?.let { assets ->
                val resolvedEntryId = entryId ?: assets.maxByOrNull { it.asset.createdAt }?.asset?.entryId
                assets.firstOrNull { it.asset.entryId == resolvedEntryId }
            }

        asset?.let {
            idReference.remove(it.asset.id)
        }
        store[treePath]?.let { assets ->
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
        treePath: String,
        recursive: Boolean,
    ): List<VariantBucketAndKey> {
        val objectStoreInformation = mutableListOf<VariantBucketAndKey>()
        if (recursive) {
            logger.info("Deleting assets (recursively) at path: $treePath")
            store.keys.filter { it.startsWith(treePath) }.forEach { path ->
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
            logger.info("Deleting assets at path: $treePath")
            val assetAndVariants = store[treePath]
            assetAndVariants?.forEach {
                objectStoreInformation.addAll(mapToBucketAndKey(it))
            }
            assetAndVariants?.map { it.asset.id }?.forEach {
                idReference.remove(it)
            }
            store.remove(treePath)
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

    private fun getNextEntryId(treePath: String): Long {
        return store[treePath]?.maxByOrNull { it.asset.entryId }?.asset?.entryId?.inc() ?: 0
    }
}

private data class InMemoryAssetAndVariants(
    val asset: Asset,
    val variants: MutableList<AssetVariant>,
)
