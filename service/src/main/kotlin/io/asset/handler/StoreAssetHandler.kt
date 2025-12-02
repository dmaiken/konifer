package io.asset.handler

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.asset.AssetStreamContainer
import io.asset.MimeTypeDetector
import io.asset.context.RequestContextFactory
import io.asset.context.StoreRequestContext
import io.asset.handler.dto.StoreAssetDto
import io.asset.model.StoreAssetRequest
import io.asset.repository.AssetRepository
import io.asset.store.ObjectStore
import io.asset.variant.VariantProfileRepository
import io.asset.variant.generation.EagerVariantGenerationJob
import io.asset.variant.generation.ImageProcessingJob
import io.asset.variant.generation.PreProcessJob
import io.asset.variant.generation.PriorityChannelScheduler
import io.image.InvalidImageException
import io.image.model.Attributes
import io.image.model.ImageFormat
import io.image.model.PreProcessedImage
import io.image.model.Transformation
import io.image.vips.createDecoderOptions
import io.image.vips.pageSafeHeight
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class StoreAssetHandler(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantJobScheduler: PriorityChannelScheduler<ImageProcessingJob<*>>,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
    private val assetStreamContainerFactory: AssetStreamContainerFactory,
    private val transformationNormalizer: TransformationNormalizer,
) {
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
            container.use { container ->
                request.validate()
                val format = deriveValidImageFormat(container.readNBytes(4096, true))
                val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)

                val processedAssetChannel = ByteChannel(true)
                container.toTemporaryFile()
                val transformation =
                    normalizePreProcessing(
                        context = context,
                        container = container,
                        sourceFormat = format,
                    )
                val persistResult =
                    async {
                        objectStore.persist(
                            bucket = context.pathConfiguration.s3PathProperties.bucket,
                            asset = processedAssetChannel,
                            format = transformation.format,
                        )
                    }

                val preProcessedDeferred = CompletableDeferred<PreProcessedImage>()
                variantJobScheduler.scheduleSynchronousJob(
                    PreProcessJob(
                        treePath = context.path,
                        lqipImplementations = context.pathConfiguration.imageProperties.previews,
                        deferredResult = preProcessedDeferred,
                        sourceFormat = format,
                        sourceContainer = container,
                        transformation = transformation,
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
                                lqipImplementations = context.pathConfiguration.imageProperties.previews,
                                bucket = context.pathConfiguration.s3PathProperties.bucket,
                            ),
                        )
                    }
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

    private suspend fun normalizePreProcessing(
        context: StoreRequestContext,
        container: AssetStreamContainer,
        sourceFormat: ImageFormat,
    ): Transformation =
        withContext(Dispatchers.IO) {
            var dimensions: Pair<Int, Int>? = null

            Vips.run { arena ->
                // Even if this image is paged, just need to load one frame to get height/width
                // So don't specify "n" as an option
                val image =
                    VImage.newFromFile(
                        arena,
                        container.getTemporaryFile().absolutePath,
                        *createDecoderOptions(
                            sourceFormat = sourceFormat,
                            destinationFormat = context.pathConfiguration.imageProperties.preProcessing.format ?: sourceFormat,
                        ),
                    )

                dimensions = Pair(image.width, image.pageSafeHeight())
            }
            val requestedTransformation = context.pathConfiguration.imageProperties.preProcessing.requestedImageTransformation

            transformationNormalizer.normalize(
                requested = requestedTransformation,
                originalVariantAttributes =
                    Attributes(
                        width = requireNotNull(dimensions).first,
                        height = dimensions.second,
                        format = sourceFormat,
                    ),
            )
        }
}
