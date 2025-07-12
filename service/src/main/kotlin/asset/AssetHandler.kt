package asset

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.StoreAssetRequest
import asset.repository.AssetRepository
import asset.store.ObjectStore
import image.ImageProcessor
import image.InvalidImageException
import image.model.RequestedImageAttributes
import io.asset.AssetStreamContainer
import io.asset.ImageAttributeAdapter
import io.ktor.http.Parameters
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.path.PathAdapter
import io.path.PathModifierOption
import io.path.configuration.PathConfiguration
import io.path.configuration.PathConfigurationService

class AssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val pathGenerator: PathAdapter,
    private val imageProcessor: ImageProcessor,
    private val objectStore: ObjectStore,
    private val pathConfigurationService: PathConfigurationService,
    private val imageAttributeAdapter: ImageAttributeAdapter,
) {
    private val logger = KtorSimpleLogger("asset")

    suspend fun storeNewAsset(
        request: StoreAssetRequest,
        container: AssetStreamContainer,
        uriPath: String,
    ): AssetAndLocation {
        val mimeType = deriveValidMimeType(container.readNBytes(1024, true))
        val pathConfiguration = validatePathConfiguration(uriPath, mimeType)
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        val preProcessed = imageProcessor.preprocess(container, mimeType, pathConfiguration)
        val persistResult = objectStore.persist(preProcessed.channel)
        val assetAndVariants =
            assetRepository.store(
                StoreAssetDto(
                    request = request,
                    mimeType = mimeType,
                    treePath = treePath,
                    imageAttributes = preProcessed.attributes,
                    persistResult = persistResult,
                ),
            )

        return AssetAndLocation(
            assetAndVariants = assetAndVariants,
            locationPath = uriPath,
        )
    }

    suspend fun fetchAssetByPath(
        uriPath: String,
        entryId: Long?,
        parameters: Parameters,
    ): String? {
        val requestedAttributes = imageAttributeAdapter.fromParameters(parameters)
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        logger.info("Fetching asset by path: $treePath")

        val assetAndVariants = assetRepository.fetchByPath(treePath, entryId, requestedAttributes)
        if (assetAndVariants == null) {
            return null
        }
        return if (assetAndVariants.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: $uriPath and entryId: $entryId")
            cacheVariant(
                treePath = assetAndVariants.asset.path,
                entryId = assetAndVariants.asset.entryId,
                requestedAttributes = requestedAttributes,
            )?.let {
                objectStore.generateObjectUrl(it.variants.first())
            }
        } else {
            logger.info("Variant found for asset with path: $uriPath and entryId: $entryId")
            objectStore.generateObjectUrl(assetAndVariants.variants.first())
        }
    }

    suspend fun fetchAssetMetadataByPath(
        uriPath: String,
        entryId: Long?,
        parameters: Parameters,
    ): AssetAndVariants? {
        val treePath = pathGenerator.toTreePathFromUriPath(uriPath)
        val imageAttributes = imageAttributeAdapter.fromParameters(parameters)
        logger.info("Fetching asset info by path: $treePath with attributes: $imageAttributes")

        return assetRepository.fetchByPath(treePath, entryId, imageAttributes)
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
    ): AssetAndVariants? {
        val original = assetRepository.fetchByPath(treePath, entryId, RequestedImageAttributes.originalVariant())
        // Defense
        if (original == null) {
            return null
        }
        val originalVariant = original.getOriginalVariant()
        val channel = ByteChannel(autoFlush = true)
        val found = objectStore.fetch(originalVariant.objectStoreBucket, originalVariant.objectStoreKey, channel)
        if (!found.found) {
            throw IllegalStateException(
                "Cannot locate object with bucket: ${originalVariant.objectStoreBucket} key: ${originalVariant.objectStoreKey}",
            )
        }
        val newVariant =
            imageProcessor.generateFrom(
                from = AssetStreamContainer(channel),
                requestedAttributes = requestedAttributes,
                generatedFromAttributes = originalVariant.attributes,
            )
        val persistResult = objectStore.persist(newVariant.channel)

        return assetRepository.storeVariant(original.asset.path, original.asset.entryId, persistResult, newVariant.attributes)
    }
}

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
