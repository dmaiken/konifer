package asset

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.StoreAssetRequest
import asset.repository.AssetRepository
import asset.store.ObjectStore
import image.InvalidImageException
import image.VipsImageProcessor
import image.model.ImageFormat
import io.asset.AssetStreamContainer
import io.asset.context.ContentTypeNotPermittedException
import io.asset.context.QueryRequestContext
import io.asset.context.RequestContextFactory
import io.asset.handler.AssetLinkDto
import io.asset.variant.VariantGenerationJob
import io.asset.variant.VariantGenerator
import io.asset.variant.VariantProfileRepository
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.path.DeleteMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

class AssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val imageProcessor: VipsImageProcessor,
    private val objectStore: ObjectStore,
    private val variantGenerator: VariantGenerator,
    private val variantGenerationChannel: Channel<VariantGenerationJob>,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
) {
    private val logger = KtorSimpleLogger("asset")

    suspend fun storeNewAsset(
        deferredRequest: CompletableDeferred<StoreAssetRequest>,
        container: AssetStreamContainer,
        uriPath: String,
    ): AssetAndLocation =
        coroutineScope {
            val format = deriveValidImageFormat(container.readNBytes(1024, true))
            val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)
            val processedAssetChannel = ByteChannel(true)
            val persistResult =
                async {
                    objectStore.persist(context.pathConfiguration.s3Properties.bucket, processedAssetChannel)
                }
            val preProcessed = imageProcessor.preprocess(container, format, context.pathConfiguration, processedAssetChannel)

            val assetAndVariants =
                assetRepository.store(
                    StoreAssetDto(
                        request = deferredRequest.await(),
                        mimeType = format.mimeType,
                        path = context.path,
                        attributes = preProcessed.attributes,
                        persistResult = persistResult.await(),
                        lqips = preProcessed.lqip,
                    ),
                )

            AssetAndLocation(
                assetAndVariants = assetAndVariants,
                locationPath = context.path,
            ).also { response ->
                val variants =
                    context.pathConfiguration.eagerVariants.map {
                        variantProfileRepository.fetch(it)
                    }
                if (variants.isNotEmpty()) {
                    variantGenerationChannel.send(
                        VariantGenerationJob(
                            treePath = response.assetAndVariants.asset.path,
                            entryId = response.assetAndVariants.asset.entryId,
                            transformations = variants,
                            pathConfiguration = context.pathConfiguration,
                        ),
                    )
                }
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
        val entryId = context.modifiers.entryId
        logger.info("Fetching asset info by path: ${context.path} with attributes: ${context.transformation}")

        val assetAndVariants =
            assetRepository.fetchByPath(context.path, entryId, context.transformation) ?: return null
        if (!generateVariant) {
            return Pair(assetAndVariants, true)
        }

        return if (assetAndVariants.variants.isEmpty() && context.transformation != null) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: $entryId")
            context.pathConfiguration.allowedContentTypes?.let {
                if (!it.contains(context.transformation.format.mimeType)) {
                    throw ContentTypeNotPermittedException("Content type: ${context.transformation.format} not permitted")
                }
            }
            return variantGenerator.generateVariant(
                treePath = assetAndVariants.asset.path,
                entryId = assetAndVariants.asset.entryId,
                pathConfiguration = context.pathConfiguration,
                transformation = context.transformation,
            ).let {
                Pair(it, false)
            }
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: $entryId")
            Pair(assetAndVariants, true)
        }
    }

    suspend fun fetchAssetMetadataInPath(context: QueryRequestContext): List<AssetAndVariants> {
        logger.info("Fetching asset info in path: ${context.path}")
        return assetRepository.fetchAllByPath(context.path, null)
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
        if (entryId == null) {
            logger.info("Deleting asset with path: $uriPath")
        } else {
            logger.info("Deleting asset with path: $uriPath and entry id: $entryId")
        }
        assetRepository.deleteAssetByPath(uriPath, entryId)
    }

    suspend fun deleteAssets(
        uriPath: String,
        mode: DeleteMode,
    ) {
        when (mode) {
            DeleteMode.SINGLE -> throw IllegalArgumentException("Delete mode of: $mode not allowed")
            DeleteMode.CHILDREN -> logger.info("Deleting assets at path: $uriPath")
            DeleteMode.RECURSIVE -> logger.info("Deleting assets at path: $uriPath and all underneath it!")
        }
        assetRepository.deleteAssetsByPath(uriPath, mode == DeleteMode.RECURSIVE)
    }

    private fun deriveValidImageFormat(content: ByteArray): ImageFormat {
        val mimeType = mimeTypeDetector.detect(content)
        if (!validate(mimeType)) {
            logger.error("Not an image type: $mimeType")
            throw InvalidImageException("Not an image type")
        }
        return ImageFormat.fromMimeType(mimeType)
    }

    private fun validate(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }
}

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
