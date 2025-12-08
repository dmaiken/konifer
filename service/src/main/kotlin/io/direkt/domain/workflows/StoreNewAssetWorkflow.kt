package io.direkt.domain.workflows

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.direkt.asset.AssetDataContainer
import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetAndLocation
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.InvalidImageException
import io.direkt.domain.ports.AssetContainerFactory
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.MimeTypeDetector
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantProfileRepository
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.vips.createDecoderOptions
import io.direkt.service.context.RequestContextFactory
import io.direkt.service.context.StoreRequestContext
import io.direkt.service.transformation.TransformationNormalizer
import io.image.vips.pageSafeHeight
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID

class StoreNewAssetWorkflow(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectRepository,
    private val variantGenerator: VariantGenerator,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
    private val assetStreamContainerFactory: AssetContainerFactory,
    private val transformationNormalizer: TransformationNormalizer,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun handleFromUpload(
        deferredRequest: CompletableDeferred<StoreAssetRequest>,
        multiPartContainer: AssetDataContainer,
        uriPath: String,
    ): AssetAndLocation =
        handle(
            request = deferredRequest.await(),
            container = multiPartContainer,
            uriPath = uriPath,
        )

    suspend fun handleFromUrl(
        request: StoreAssetRequest,
        uriPath: String,
    ): AssetAndLocation =
        handle(
            request = request,
            container = assetStreamContainerFactory.fromUrlSource(request.url),
            uriPath = uriPath,
        )

    private suspend fun handle(
        request: StoreAssetRequest,
        container: AssetDataContainer,
        uriPath: String,
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

                val objectStoreKey = "${UUID.randomUUID()}${preProcessed.attributes.format.extension}"
                val pendingAsset = newAsset.markPending(
                    originalVariant = Variant.Pending.originalVariant(
                        assetId = newAsset.id,
                        attributes = preProcessed.attributes,
                        objectStoreBucket = context.pathConfiguration.s3PathProperties.bucket,
                        objectStoreKey = objectStoreKey,
                        lqip = preProcessed.lqip,
                    )
                )

                val pendingPersisted = assetRepository.storeNew(pendingAsset)
                val originalVariant = pendingPersisted.variants.first()
                val uploadedAt =
                    objectStore.persist(
                        bucket = originalVariant.objectStoreBucket,
                        key = objectStoreKey,
                        asset = preProcessed.result,
                    )
                val ready = pendingPersisted.markReady(uploadedAt)

                AssetAndLocation(
                    asset = ready,
                    locationPath = context.path,
                ).also { response ->
                    val eagerVariantTransformations =
                        context.pathConfiguration.eagerVariants.map {
                            variantProfileRepository.fetch(it)
                        }
                    if (eagerVariantTransformations.isNotEmpty()) {
                        variantGenerator.initiateEagerVariants(
                            path = response.asset.path,
                            entryId = checkNotNull(response.asset.entryId),
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
        container: AssetDataContainer,
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
