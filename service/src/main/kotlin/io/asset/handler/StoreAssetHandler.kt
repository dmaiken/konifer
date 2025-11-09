package io.asset.handler

import io.asset.AssetStreamContainer
import io.asset.MimeTypeDetector
import io.asset.context.RequestContextFactory
import io.asset.model.StoreAssetRequest
import io.asset.repository.AssetRepository
import io.asset.store.ObjectStore
import io.asset.variant.VariantProfileRepository
import io.asset.variant.generation.EagerVariantGenerationJob
import io.asset.variant.generation.ImageProcessingJob
import io.asset.variant.generation.PreProcessJob
import io.asset.variant.generation.PriorityChannelScheduler
import io.image.InvalidImageException
import io.image.model.ImageFormat
import io.image.model.PreProcessedImage
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class StoreAssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantJobScheduler: PriorityChannelScheduler<ImageProcessingJob<*>>,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
    private val assetStreamContainerFactory: AssetStreamContainerFactory,
) {
    companion object {
        /**
         * Looks like some assistive devices truncate alts after 125 characters
         */
        private const val MAX_ALT_LENGTH: Int = 125

        /**
         * Inspired from AWS limit
         */
        private const val MAX_LABEL_KEY_LENGTH: Int = 128

        /**
         * Inspired from AWS limit
         */
        private const val MAX_LABEL_VALUE_LENGTH: Int = 256

        /**
         * Inspired from AWS limit
         */
        private const val MAX_LABELS: Int = 50

        private const val MAX_TAG_VALUE_LENGTH: Int = 256
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun storeNewAssetFromUpload(
        deferredRequest: CompletableDeferred<StoreAssetRequest>,
        multiPartContainer: AssetStreamContainer,
        uriPath: String,
    ): AssetAndLocation =
        storeAsset(
            request = deferredRequest.await(),
            container = multiPartContainer,
            uriPath = uriPath,
            source = AssetSource.UPLOAD,
        )

    suspend fun storeNewAssetFromUrl(
        request: StoreAssetRequest,
        uriPath: String,
    ): AssetAndLocation =
        storeAsset(
            request = request,
            container = assetStreamContainerFactory.fromUrlSource(request.url),
            uriPath = uriPath,
            source = AssetSource.URL,
        )

    private suspend fun storeAsset(
        request: StoreAssetRequest,
        container: AssetStreamContainer,
        uriPath: String,
        source: AssetSource,
    ): AssetAndLocation =
        coroutineScope {
            validateRequest(request)
            val format = deriveValidImageFormat(container.readNBytes(4096, true))
            val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)
            val processedAssetChannel = ByteChannel(true)
            val persistResult =
                async {
                    objectStore.persist(context.pathConfiguration.s3PathProperties.bucket, processedAssetChannel)
                }
            val preProcessedDeferred = CompletableDeferred<PreProcessedImage>()
            variantJobScheduler.scheduleSynchronousJob(
                PreProcessJob(
                    treePath = context.path,
                    pathConfiguration = context.pathConfiguration,
                    deferredResult = preProcessedDeferred,
                    sourceFormat = format,
                    sourceContainer = container,
                    outputChannel = processedAssetChannel,
                ),
            )
            val preProcessed = preProcessedDeferred.await()

            val assetAndVariants =
                assetRepository.store(
                    StoreAssetDto(
                        request = request,
                        path = context.path,
                        attributes = preProcessed.attributes,
                        persistResult = persistResult.await(),
                        lqips = preProcessed.lqip,
                        source = source,
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
                    variantJobScheduler.scheduleBackgroundJob(
                        EagerVariantGenerationJob(
                            treePath = response.assetAndVariants.asset.path,
                            entryId = response.assetAndVariants.asset.entryId,
                            requestedTransformations = variants,
                            pathConfiguration = context.pathConfiguration,
                        ),
                    )
                }
            }
        }

    private fun deriveValidImageFormat(content: ByteArray): ImageFormat {
        val mimeType = mimeTypeDetector.detect(content)
        if (!validate(mimeType)) {
            logger.error("Not an image type: $mimeType")
            throw InvalidImageException("Not an image type")
        }
        return ImageFormat.fromMimeType(mimeType)
    }

    private fun validate(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun validateRequest(request: StoreAssetRequest) {
        if (request.alt != null && request.alt.length > MAX_ALT_LENGTH) {
            throw IllegalArgumentException("Alt exceeds max length of $MAX_ALT_LENGTH")
        }
        if (request.labels.any { it.key.length > MAX_LABEL_KEY_LENGTH || it.value.length > MAX_LABEL_VALUE_LENGTH }) {
            throw IllegalArgumentException("Labels exceed max length of ($MAX_LABEL_KEY_LENGTH, $MAX_LABEL_VALUE_LENGTH)")
        }
        if (request.labels.size > MAX_LABELS) {
            throw IllegalArgumentException("Cannot have more than $MAX_LABELS labels")
        }
        if (request.tags.any { it.length > MAX_TAG_VALUE_LENGTH }) {
            throw IllegalArgumentException("Tags exceed max length of $MAX_TAG_VALUE_LENGTH")
        }
    }
}
