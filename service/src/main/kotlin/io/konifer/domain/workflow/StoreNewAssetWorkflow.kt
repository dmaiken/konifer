package io.konifer.domain.workflow

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetAndLocation
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.InvalidImageException
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.MimeTypeDetector
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.service.TemporaryFileFactory
import io.konifer.service.context.RequestContextFactory
import io.konifer.service.context.StoreRequestContext
import io.konifer.service.transformation.TransformationNormalizer
import io.konifer.service.variant.ObjectStoreKeyFactory
import io.konifer.service.variant.VariantService
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
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
                val eagerVariantTransformations =
                    context.pathConfiguration.eagerVariants.map {
                        variantProfileRepository.fetch(it)
                    }
                hasEagerVariants = eagerVariantTransformations.isNotEmpty()
                val (preprocessedFile, readyAsset) =
                    if (context.requiresPreProcessing()) {
                        handleWithPreProcessing(
                            newAsset = newAsset,
                            context = context,
                            container = container,
                            sourceFormat = format,
                            hasEagerVariants = hasEagerVariants,
                        )
                    } else {
                        handleWithoutPreProcessing(
                            newAsset = newAsset,
                            context = context,
                            container = container,
                            sourceFormat = format,
                        )
                    }
                val originalVariant = readyAsset.variants.first()

                AssetAndLocation(
                    asset = readyAsset,
                    locationPath = context.path,
                ).also {
                    if (hasEagerVariants) {
                        createVariantsScope.launch {
                            container.use {
                                try {
                                    variantService.createEagerVariants(
                                        originalVariantFile = preprocessedFile,
                                        requestedTransformations = eagerVariantTransformations,
                                        assetId = readyAsset.id,
                                        originalVariantAttributes = originalVariant.attributes,
                                        lqipImplementations = context.pathConfiguration.image.previews,
                                        originalVariantLQIPs = originalVariant.lqips,
                                        bucket = context.pathConfiguration.objectStore.bucket,
                                    )
                                } finally {
                                    preprocessedFile.deleteIfExists()
                                }
                            }
                        }
                    }
                }
            } finally {
                if (!hasEagerVariants) {
                    container.close()
                } else {
                    container.closeChannel()
                }
            }
        }

    private suspend fun handleWithPreProcessing(
        newAsset: Asset.New,
        context: StoreRequestContext,
        container: AssetDataContainer,
        sourceFormat: ImageFormat,
        hasEagerVariants: Boolean,
    ): Pair<Path, Asset.Ready> =
        coroutineScope {
            val transformation =
                normalizePreProcessing(
                    context = context,
                    container = container,
                    sourceFormat = sourceFormat,
                )
            val preprocessedDataContainer =
                TransformationDataContainer(
                    transformation = transformation,
                )
            val generationJob =
                variantGenerator
                    .preProcessOriginalVariant(
                        sourceFormat = sourceFormat,
                        lqipImplementations = context.pathConfiguration.image.previews,
                        transformationDataContainer = preprocessedDataContainer,
                        source = container.getTemporaryFile(),
                    )
            val (preProcessedFile, objectStoreChannel) =
                if (hasEagerVariants) {
                    val eagerVariantInputFile = TemporaryFileFactory.createPreProcessedTempFile(transformation.format.extension)
                    val channel = ByteChannel()
                    launch {
                        teeStream(
                            source = preprocessedDataContainer.output,
                            firstChannel = channel,
                            secondChannel = eagerVariantInputFile.toFile().writeChannel(),
                        )
                    }
                    Pair(eagerVariantInputFile, channel)
                } else {
                    Pair(container.getTemporaryFile(), preprocessedDataContainer.output)
                }
            val objectStoreBucket = context.pathConfiguration.objectStore.bucket
            val objectStoreKey = ObjectStoreKeyFactory.newKey(preprocessedDataContainer.attributes.await().format)
            val uploadJob =
                async {
                    objectStore.persist(
                        bucket = objectStoreBucket,
                        key = objectStoreKey,
                        channel = objectStoreChannel,
                    )
                }

            val pendingAsset =
                newAsset.markPending(
                    originalVariant =
                        Variant.Pending.originalVariant(
                            assetId = newAsset.id,
                            attributes = preprocessedDataContainer.attributes.await(),
                            objectStoreBucket = objectStoreBucket,
                            objectStoreKey = objectStoreKey,
                            lqip = preprocessedDataContainer.lqips.await() ?: LQIPs.NONE,
                        ),
                )

            val pendingPersisted = assetRepository.storeNew(pendingAsset)
            val uploadedAt = uploadJob.await()
            generationJob.join()

            pendingPersisted.markReady(uploadedAt).let {
                logger.info("Asset: ${pendingPersisted.descriptor} uploaded at $uploadedAt after preprocessing, marking as ready")
                assetRepository.markReady(it)
                Pair(preProcessedFile, it)
            }
        }

    private suspend fun handleWithoutPreProcessing(
        newAsset: Asset.New,
        context: StoreRequestContext,
        sourceFormat: ImageFormat,
        container: AssetDataContainer,
    ): Pair<Path, Asset.Ready> =
        coroutineScope {
            // Skip preprocessing entirely if not required
            val attributes =
                withContext(Dispatchers.IO) {
                    Attributes.createAttributes(
                        path = container.getTemporaryFile(),
                        format = sourceFormat,
                    )
                }
            val objectStoreKey = ObjectStoreKeyFactory.newKey(attributes.format)
            val pendingAsset =
                newAsset.markPending(
                    originalVariant =
                        Variant.Pending.originalVariant(
                            assetId = newAsset.id,
                            attributes = attributes,
                            objectStoreBucket = context.pathConfiguration.objectStore.bucket,
                            objectStoreKey = objectStoreKey,
                            lqip = LQIPs.NONE,
                        ),
                )

            val pendingPersisted = assetRepository.storeNew(pendingAsset)
            val originalVariant = pendingPersisted.variants.first()
            val uploadedAt =
                objectStore.persist(
                    bucket = originalVariant.objectStoreBucket,
                    key = objectStoreKey,
                    file = container.getTemporaryFile(),
                )
            logger.info("Asset: ${pendingPersisted.descriptor} uploaded at $uploadedAt without preprocessing, marking as ready")
            pendingPersisted.markReady(uploadedAt).let {
                assetRepository.markReady(it)
                Pair(container.getTemporaryFile(), it)
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
