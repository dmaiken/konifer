package io.direkt.domain.workflow

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetAndLocation
import io.direkt.domain.asset.AssetDataContainer
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
import io.direkt.infrastructure.vips.pageSafeHeight
import io.direkt.service.TemporaryFileFactory
import io.direkt.service.context.RequestContextFactory
import io.direkt.service.context.StoreRequestContext
import io.direkt.service.transformation.TransformationNormalizer
import io.direkt.service.variant.VariantService
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.io.path.pathString

class StoreNewAssetWorkflow(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectRepository,
    private val variantService: VariantService,
    private val variantGenerator: VariantGenerator,
    private val variantProfileRepository: VariantProfileRepository,
    private val requestContextFactory: RequestContextFactory,
    private val assetStreamContainerFactory: AssetContainerFactory,
    private val transformationNormalizer: TransformationNormalizer,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    private val exceptionHandler =
        CoroutineExceptionHandler { _, exception ->
            logger.error("Variant generation failed", exception)
        }
    private val createVariantsScope = CoroutineScope(SupervisorJob() + exceptionHandler)

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
            var hasEagerVariants = false
            try {
                val format = deriveValidImageFormat(container.readNBytes(4096, true))
                val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)
                val newAsset =
                    Asset.New.fromHttpRequest(
                        path = context.path,
                        request = request,
                    )

                container.toTemporaryFile(format.extension)
                val transformation =
                    normalizePreProcessing(
                        context = context,
                        container = container,
                        sourceFormat = format,
                    )

                val preProcessedOutput =
                    TemporaryFileFactory.createPreProcessedTempFile(
                        extension = transformation.format.extension,
                    )
                try {
                    val preProcessed =
                        variantGenerator
                            .preProcessOriginalVariant(
                                sourceFormat = format,
                                lqipImplementations = context.pathConfiguration.image.previews,
                                transformation = transformation,
                                source = container.getTemporaryFile(),
                                output = preProcessedOutput,
                            ).await()

                    val objectStoreKey = "${UUID.randomUUID()}${preProcessed.attributes.format.extension}"
                    val pendingAsset =
                        newAsset.markPending(
                            originalVariant =
                                Variant.Pending.originalVariant(
                                    assetId = newAsset.id,
                                    attributes = preProcessed.attributes,
                                    objectStoreBucket = context.pathConfiguration.objectStore.bucket,
                                    objectStoreKey = objectStoreKey,
                                    lqip = preProcessed.lqip,
                                ),
                        )

                    val pendingPersisted = assetRepository.storeNew(pendingAsset)
                    val originalVariant = pendingPersisted.variants.first()
                    val uploadedAt =
                        objectStore.persist(
                            bucket = originalVariant.objectStoreBucket,
                            key = objectStoreKey,
                            file = preProcessedOutput.toFile(),
                        )
                    logger.info("Asset uploaded at $uploadedAt, marking as ready")
                    val readyAsset =
                        pendingPersisted.markReady(uploadedAt).also {
                            assetRepository.markReady(it)
                        }

                    AssetAndLocation(
                        asset = readyAsset,
                        locationPath = context.path,
                    ).also {
                        val eagerVariantTransformations =
                            context.pathConfiguration.eagerVariants.map {
                                variantProfileRepository.fetch(it)
                            }
                        if (eagerVariantTransformations.isNotEmpty()) {
                            hasEagerVariants = true
                            createVariantsScope.launch {
                                container.use { container ->
                                    variantService.createEagerVariants(
                                        originalVariantFile = container.getTemporaryFile(),
                                        requestedTransformations = eagerVariantTransformations,
                                        assetId = readyAsset.id,
                                        originalVariantAttributes = originalVariant.attributes,
                                        lqipImplementations = context.pathConfiguration.image.previews,
                                        originalVariantLQIPs = originalVariant.lqips,
                                        bucket = context.pathConfiguration.objectStore.bucket,
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    if (!hasEagerVariants) {
                        preProcessedOutput.toFile().delete()
                    }
                }
            } finally {
                if (!hasEagerVariants) {
                    container.close()
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
                        container.getTemporaryFile().pathString,
                        *createDecoderOptions(
                            sourceFormat = sourceFormat,
                            destinationFormat =
                                context.pathConfiguration.preProcessing.image.format
                                    ?: sourceFormat,
                        ),
                    )

                dimensions = Pair(image.width, image.pageSafeHeight())
            }
            val requestedTransformation =
                context.pathConfiguration.preProcessing.image.requestedImageTransformation

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
