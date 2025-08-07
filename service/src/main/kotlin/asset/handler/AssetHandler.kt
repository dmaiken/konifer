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
import io.asset.context.QueryRequestContext
import io.asset.handler.AssetLinkDto
import io.asset.variant.VariantGenerationJob
import io.asset.variant.VariantGenerator
import io.asset.variant.VariantProfileRepository
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.path.DeleteMode
import io.path.PathAdapter
import io.path.configuration.PathConfiguration
import io.path.configuration.PathConfigurationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

class AssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val imageProcessor: VipsImageProcessor,
    private val objectStore: ObjectStore,
    private val pathConfigurationRepository: PathConfigurationRepository,
    private val variantGenerator: VariantGenerator,
    private val variantGenerationChannel: Channel<VariantGenerationJob>,
    private val variantProfileRepository: VariantProfileRepository,
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
            val treePath = PathAdapter.toTreePathFromUriPath(uriPath)
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
            ).also { response ->
                val variants =
                    pathConfiguration.eagerVariants.map {
                        variantProfileRepository.fetch(it)
                    }
                variantGenerationChannel.send(
                    VariantGenerationJob(
                        treePath = response.assetAndVariants.asset.path,
                        entryId = response.assetAndVariants.asset.entryId,
                        requestedImageAttributes = variants,
                    ),
                )
            }
        }

    suspend fun fetchAssetLinksByPath(context: QueryRequestContext): AssetLinkDto? {
        val assetAndCacheStatus = fetchAssetMetadataByPath(context, true) ?: return null
        logger.info("Found asset with response: $assetAndCacheStatus and route: ${context.path}")
        val variant = assetAndCacheStatus.first.variants.first()

        return AssetLinkDto(
            url = objectStore.generateObjectUrl(variant),
            cacheHit = assetAndCacheStatus.second,
            lqip = variant.lqip,
        )
    }

    suspend fun fetchAssetMetadataByPath(
        context: QueryRequestContext,
        generateVariant: Boolean,
    ): Pair<AssetAndVariants, Boolean>? {
        val treePath = PathAdapter.toTreePathFromUriPath(context.path)
        val entryId = context.modifiers.entryId
        logger.info("Fetching asset info by path: $treePath with attributes: ${context.requestedImageAttributes}")

        val assetAndVariants = assetRepository.fetchByPath(treePath, entryId, context.requestedImageAttributes)
        if (assetAndVariants == null) {
            return null
        }
        if (!generateVariant) {
            return Pair(assetAndVariants, true)
        }

        return if (assetAndVariants.variants.isEmpty() && context.requestedImageAttributes != null) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: $entryId")
            val requestedMimeType =
                context.requestedImageAttributes.mimeType ?: assetRepository.fetchByPath(
                    treePath, entryId, RequestedImageAttributes.ORIGINAL_VARIANT,
                )?.getOriginalVariant()?.attributes?.mimeType ?: throw IllegalStateException(
                    "No original variant found for asset at path: ${context.path} and entryId: $entryId",
                )
            validatePathConfiguration(context.path, requestedMimeType)
            return variantGenerator.generateVariant(
                treePath = assetAndVariants.asset.path,
                entryId = assetAndVariants.asset.entryId,
                requestedAttributes = context.requestedImageAttributes,
            ).let {
                Pair(it, false)
            }
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: $entryId")
            Pair(assetAndVariants, true)
        }
    }

    suspend fun fetchAssetMetadataInPath(context: QueryRequestContext): List<AssetAndVariants> {
        val treePath = PathAdapter.toTreePathFromUriPath(context.path)
        logger.info("Fetching asset info in path: $treePath")
        return assetRepository.fetchAllByPath(treePath, null)
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
        val treePath = PathAdapter.toTreePathFromUriPath(uriPath)
        if (entryId == null) {
            logger.info("Deleting asset with path: $treePath")
        } else {
            logger.info("Deleting asset with path: $treePath and entry id: $entryId")
        }
        assetRepository.deleteAssetByPath(treePath, entryId)
    }

    suspend fun deleteAssets(
        uriPath: String,
        mode: DeleteMode,
    ) {
        val treePath = PathAdapter.toTreePathFromUriPath(uriPath)
        when (mode) {
            DeleteMode.SINGLE -> throw IllegalArgumentException("Delete mode of: $mode not allowed")
            DeleteMode.CHILDREN -> logger.info("Deleting assets at path: $treePath")
            DeleteMode.RECURSIVE -> logger.info("Deleting assets at path: $treePath and all underneath it!")
        }
        assetRepository.deleteAssetsByPath(treePath, mode == DeleteMode.RECURSIVE)
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
        return pathConfigurationRepository.fetch(uriPath).also { config ->
            config.allowedContentTypes?.contains(mimeType)?.let { allowedContentTypes ->
                if (!allowedContentTypes) {
                    throw IllegalArgumentException("Not an allowed content type: $mimeType for path: $uriPath")
                }
            }
        }
    }
}

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
