package io.direkt.workflows

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.direkt.asset.AssetStreamContainer
import io.direkt.asset.MimeTypeDetector
import io.direkt.asset.context.RequestContextFactory
import io.direkt.asset.context.StoreRequestContext
import io.direkt.asset.handler.AssetAndLocation
import io.direkt.asset.handler.AssetSource
import io.direkt.asset.handler.AssetStreamContainerFactory
import io.direkt.asset.handler.TransformationNormalizer
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.repository.AssetRepository
import io.direkt.asset.store.ObjectStore
import io.direkt.asset.variant.VariantProfileRepository
import io.direkt.domain.aggregate.Asset
import io.direkt.domain.ports.VariantGenerator
import io.direkt.image.InvalidImageException
import io.direkt.image.model.Attributes
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.Transformation
import io.direkt.image.vips.createDecoderOptions
import io.image.vips.pageSafeHeight
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class StoreNewAssetWorkflow(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantGenerator: VariantGenerator,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
    private val assetStreamContainerFactory: AssetStreamContainerFactory,
    private val transformationNormalizer: TransformationNormalizer,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun handleFromUpload(
        deferredRequest: CompletableDeferred<StoreAssetRequest>,
        multiPartContainer: AssetStreamContainer,
        uriPath: String,
    ): AssetAndLocation =
        handle(
            request = deferredRequest.await(),
            container = multiPartContainer,
            uriPath = uriPath,
            source = AssetSource.UPLOAD,
        )

    suspend fun handleFromUrl(
        request: StoreAssetRequest,
        uriPath: String,
    ): AssetAndLocation =
        handle(
            request = request,
            container = assetStreamContainerFactory.fromUrlSource(request.url),
            uriPath = uriPath,
            source = AssetSource.URL,
        )

    private suspend fun handle(
        request: StoreAssetRequest,
        container: AssetStreamContainer,
        uriPath: String,
        source: AssetSource,
    ): AssetAndLocation =
        coroutineScope {
            container.use { container ->
                val format = deriveValidImageFormat(container.readNBytes(4096, true))
                val newAsset =
                    Asset.New.fromHttpRequest(
                        path = uriPath,
                        request = request,
                    )
                val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)

                container.toTemporaryFile(format.extension)
                val transformation =
                    normalizePreProcessing(
                        context = context,
                        container = container,
                        sourceFormat = format,
                    )

                val preProcessed =
                    variantGenerator
                        .preProcessOriginalVariant(
                            sourceFormat = format,
                            lqipImplementations = context.pathConfiguration.imageProperties.previews,
                            transformation = transformation,
                            source = container.getTemporaryFile(),
                        ).await()

                val persistResult =
                    objectStore.persist(
                        bucket = context.pathConfiguration.s3PathProperties.bucket,
                        asset = preProcessed.result,
                        format = transformation.format,
                    )
                val assetAndVariants =
                    assetRepository.store(
                        StoreAssetDto(
                            request = request,
                            path = context.path,
                            attributes = preProcessed.attributes,
                            persistResult = persistResult,
                            lqips = preProcessed.lqip,
                            source = source,
                        ),
                    )

                AssetAndLocation(
                    assetAndVariants = assetAndVariants,
                    locationPath = context.path,
                ).also { response ->
                    val eagerVariantTransformations =
                        context.pathConfiguration.eagerVariants.map {
                            variantProfileRepository.fetch(it)
                        }
                    if (eagerVariantTransformations.isNotEmpty()) {
                        variantGenerator.initiateEagerVariants(
                            path = response.assetAndVariants.asset.path,
                            entryId = response.assetAndVariants.asset.entryId,
                            requestedTransformations = eagerVariantTransformations,
                            lqipImplementations = context.pathConfiguration.imageProperties.previews,
                            bucket = context.pathConfiguration.s3PathProperties.bucket,
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
                            destinationFormat =
                                context.pathConfiguration.imageProperties.preProcessing.format
                                    ?: sourceFormat,
                        ),
                    )

                dimensions = Pair(image.width, image.pageSafeHeight())
            }
            val requestedTransformation =
                context.pathConfiguration.imageProperties.preProcessing.requestedImageTransformation

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
