package io.konifer.domain.workflow

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetAndLocation
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.InvalidImageException
import io.konifer.domain.image.PreProcessedImage
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.MimeTypeDetector
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.service.TemporaryFileFactory
import io.konifer.service.context.RequestContextFactory
import io.konifer.service.context.StoreRequestContext
import io.konifer.service.transformation.TransformationNormalizer
import io.konifer.service.variant.VariantService
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.io.path.pathString

class StoreNewAssetWorkflow(
    private val mimeTypeDetector: MimeTypeDetector,
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
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
                val format = deriveValidImageFormat(container.peek(1024))
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

                var preProcessedPath =
                    TemporaryFileFactory.createPreProcessedTempFile(
                        extension = transformation.format.extension,
                    )
                try {
                    val preProcessed =
                        if (context.requiresPreProcessing()) {
                            variantGenerator
                                .preProcessOriginalVariant(
                                    sourceFormat = format,
                                    lqipImplementations = context.pathConfiguration.image.previews,
                                    transformation = transformation,
                                    source = container.getTemporaryFile(),
                                    output = preProcessedPath,
                                ).await()
                        } else {
                            // Skip preprocessing entirely if not required
                            preProcessedPath = container.getTemporaryFile()
                            PreProcessedImage(
                                attributes =
                                    withContext(Dispatchers.IO) {
                                        Attributes.createAttributes(
                                            path = preProcessedPath,
                                            format = format,
                                        )
                                    },
                                lqip = LQIPs.NONE,
                            )
                        }

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
                            file = preProcessedPath.toFile(),
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
                        preProcessedPath.toFile().delete()
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
            val requestedTransformation =
                context.pathConfiguration.preProcessing.image.requestedImageTransformation
            var transformation: Transformation? = null

            Vips.run { arena ->
                val destinationFormat =
                    context.pathConfiguration.preProcessing.image.format
                        ?: sourceFormat
                // Even if this image is paged, just need to load one frame to get height/width
                // So don't specify "n" as an option
                val image =
                    VImage.newFromFile(
                        arena,
                        container.getTemporaryFile().pathString,
                        *createDecoderOptions(
                            sourceFormat = sourceFormat,
                            destinationFormat = destinationFormat,
                        ),
                    )

                transformation =
                    runBlocking {
                        transformationNormalizer.normalize(
                            requested = requestedTransformation,
                            originalVariantAttributes =
                                Attributes.createAttributes(
                                    image = image,
                                    sourceFormat = sourceFormat,
                                    destinationFormat = destinationFormat,
                                ),
                        )
                    }
            }
            checkNotNull(transformation)
        }
}
