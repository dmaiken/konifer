package asset

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.StoreAssetRequest
import asset.repository.AssetRepository
import asset.store.ObjectStore
import image.InvalidImageException
import image.VipsImageProcessor
import image.model.RequestedImageAttributes
import io.asset.AssetStreamContainer
import io.asset.ImageAttributeAdapter
import io.asset.handler.StoreAssetVariantDto
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.path.PathAdapter
import io.path.PathModifierOption
import io.path.configuration.PathConfiguration
import io.path.configuration.PathConfigurationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val pathGenerator: PathAdapter,
    private val imageProcessor: VipsImageProcessor,
    private val objectStore: ObjectStore,
    private val pathConfigurationService: PathConfigurationService,
    private val imageAttributeAdapter: ImageAttributeAdapter,
) {
    private val logger = KtorSimpleLogger("asset")

    suspend fun storeNewAsset(
        deferredRequest: CompletableDeferred<StoreAssetRequest>,
        container: AssetStreamContainer,
        uriPath: String,
    ): AssetAndLocation =
        coroutineScope {
            val mimeType = deriveValidMimeType(container.readNBytes(1024, true))
            val pathConfiguration = validatePathConfiguration(uriPath, mimeType)
            val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
            val processedAssetChannel = ByteChannel(true)
            val persistResult =
                async {
                    objectStore.persist(processedAssetChannel)
                }
            val preProcessed = imageProcessor.preprocess(container, mimeType, pathConfiguration, processedAssetChannel)

            val assetAndVariants =
                assetRepository.store(
                    StoreAssetDto(
                        request = deferredRequest.await(),
                        mimeType = mimeType,
                        treePath = treePath,
                        imageAttributes = preProcessed.attributes,
                        persistResult = persistResult.await(),
                        lqips = preProcessed.lqip,
                    ),
                )

            AssetAndLocation(
                assetAndVariants = assetAndVariants,
                locationPath = uriPath,
            )
        }

    suspend fun fetchAssetByPath(
        uriPath: String,
        entryId: Long?,
        parameters: Parameters,
    ): Pair<String, Boolean>? {
        val assetAndCacheStatus = fetchAssetMetadataByPath(uriPath, entryId, parameters)

        if (assetAndCacheStatus == null) {
            return null
        }

        return Pair(objectStore.generateObjectUrl(assetAndCacheStatus.first.variants.first()), assetAndCacheStatus.second)
    }

    suspend fun fetchAssetMetadataByPath(
        uriPath: String,
        entryId: Long?,
        parameters: Parameters,
    ): Pair<AssetAndVariants, Boolean>? {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        val requestedAttributes = imageAttributeAdapter.fromParameters(parameters)
        logger.info("Fetching asset info by path: $treePath with attributes: $requestedAttributes")

        val assetAndVariants = assetRepository.fetchByPath(treePath, entryId, requestedAttributes)
        if (assetAndVariants == null) {
            return null
        }

        return if (assetAndVariants.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: $uriPath and entryId: $entryId")
            val requestedMimeType =
                requestedAttributes.mimeType ?: assetRepository.fetchByPath(
                    treePath, entryId, RequestedImageAttributes.ORIGINAL_VARIANT,
                )?.getOriginalVariant()?.attributes?.mimeType ?: throw IllegalStateException(
                    "No original variant found for asset at path: $uriPath and entryId: $entryId",
                )
            validatePathConfiguration(uriPath, requestedMimeType)
            return cacheVariant(
                treePath = assetAndVariants.asset.path,
                entryId = assetAndVariants.asset.entryId,
                requestedAttributes = requestedAttributes,
            )?.let {
                Pair(it, false)
            }
        } else {
            logger.info("Variant found for asset with path: $uriPath and entryId: $entryId")
            Pair(assetAndVariants, true)
        }
    }

    suspend fun fetchAssetMetadataByPath(
        uriPath: String,
        entryId: Long?,
    ): AssetAndVariants? {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        logger.info("Fetching asset info by path: $treePath")
        return assetRepository.fetchByPath(treePath, entryId, null)
    }

    suspend fun fetchAssetInfoInPath(uriPath: String): List<AssetAndVariants> {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        logger.info("Fetching asset info in path: $treePath")
        return assetRepository.fetchAllByPath(treePath)
    }

    suspend fun fetchAssetContent(
        bucket: String,
        storeKey: String,
        stream: ByteWriteChannel,
    ): Long {
        return objectStore.fetch(bucket, storeKey, stream)
            .takeIf { it.found }?.contentLength
            ?: throw IllegalStateException("Asset not found in object store: $bucket/$storeKey")
    }

    suspend fun deleteAsset(
        uriPath: String,
        entryId: Long? = null,
    ) {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        if (entryId == null) {
            logger.info("Deleting asset with path: $treePath")
        } else {
            logger.info("Deleting asset with path: $treePath and entry id: $entryId")
        }
        assetRepository.deleteAssetByPath(treePath, entryId)
    }

    suspend fun deleteAssets(
        uriPath: String,
        mode: PathModifierOption,
    ) {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        if (mode == PathModifierOption.CHILDREN) {
            logger.info("Deleting assets at path: $treePath")
        } else {
            logger.info("Deleting assets at path: $treePath and all underneath it!")
        }
        assetRepository.deleteAssetsByPath(treePath, mode == PathModifierOption.RECURSIVE)
    }

    private fun deriveValidMimeType(content: ByteArray): String {
        val mimeType = mimeTypeDetector.detect(content)
        if (!validate(mimeType)) {
            logger.error("Not an image type: $mimeType")
            throw InvalidImageException("Not an image type")
        }
        return mimeType
    }

    private fun validate(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    private fun validatePathConfiguration(
        uriPath: String,
        mimeType: String,
    ): PathConfiguration {
        return pathConfigurationService.fetchConfigurationForPath(uriPath).also { config ->
            config.allowedContentTypes?.contains(mimeType)?.let { allowedContentTypes ->
                if (!allowedContentTypes) {
                    throw IllegalArgumentException("Not an allowed content type: $mimeType for path: $uriPath")
                }
            }
        }
    }

    private suspend fun cacheVariant(
        treePath: String,
        entryId: Long,
        requestedAttributes: RequestedImageAttributes,
    ): AssetAndVariants? =
        coroutineScope {
            val original = assetRepository.fetchByPath(treePath, entryId, RequestedImageAttributes.ORIGINAL_VARIANT)
            // Defense
            if (original == null) {
                return@coroutineScope null
            }
            val originalVariant = original.getOriginalVariant()
            val found = objectStore.exists(originalVariant.objectStoreBucket, originalVariant.objectStoreKey)
            if (!found) {
                throw IllegalStateException(
                    "Cannot locate object with bucket: ${originalVariant.objectStoreBucket} key: ${originalVariant.objectStoreKey}",
                )
            }
            val originalVariantChannel = ByteChannel(true)
            val fetchOriginalVariantJob =
                launch {
                    objectStore.fetch(originalVariant.objectStoreBucket, originalVariant.objectStoreKey, originalVariantChannel)
                }
            val processedAssetChannel = ByteChannel(true)
            val persistResult =
                async {
                    objectStore.persist(processedAssetChannel)
                }
            val newVariant =
                imageProcessor.generateVariant(
                    source = AssetStreamContainer(originalVariantChannel),
                    requestedAttributes = requestedAttributes,
                    originalVariant = originalVariant,
                    outputChannel = processedAssetChannel,
                )
            fetchOriginalVariantJob.join()

            assetRepository.storeVariant(
                StoreAssetVariantDto(
                    treePath = original.asset.path,
                    entryId = original.asset.entryId,
                    persistResult = persistResult.await(),
                    imageAttributes = newVariant.attributes,
                    lqips = newVariant.lqip,
                ),
            )
        }
}

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
