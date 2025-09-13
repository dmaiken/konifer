package io.asset.variant

import asset.Asset
import asset.model.AssetAndVariants
import asset.repository.AssetRepository
import asset.store.ObjectStore
import asset.variant.AssetVariant
import image.VipsImageProcessor
import image.model.RequestedImageAttributes
import io.asset.AssetStreamContainer
import io.asset.handler.StoreAssetVariantDto
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VariantGenerator(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val imageProcessor: VipsImageProcessor,
    private val channel: Channel<VariantGenerationJob>,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, exception ->
            logger.error("Variant generation failed", exception)
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    init {
        start()
    }

    fun start() {
        scope.launch {
            logger.info("Starting variant generator queue listener")
            while (isActive) {
                val job = channel.receive()
                logger.info("Received variant generation job: {}", job)
                try {
                    generateVariants(
                        treePath = job.treePath,
                        entryId = job.entryId,
                        pathConfiguration = job.pathConfiguration,
                        requestedAttributes = job.requestedImageAttributes,
                    ).also {
                        job.deferredResult?.complete(it)
                    }
                } catch (e: Exception) {
                    logger.error("Error while generating variant generation with request: {}", job, e)
                    job.deferredResult?.completeExceptionally(e)
                }
            }
            logger.info("Shut down variant generator queue listener")
        }
    }

    suspend fun generateVariant(
        treePath: String,
        entryId: Long,
        pathConfiguration: PathConfiguration,
        requestedAttributes: RequestedImageAttributes,
    ): AssetAndVariants = generateVariants(treePath, entryId, pathConfiguration, listOf(requestedAttributes))

    private suspend fun generateVariants(
        treePath: String,
        entryId: Long,
        pathConfiguration: PathConfiguration,
        requestedAttributes: List<RequestedImageAttributes>,
    ): AssetAndVariants =
        coroutineScope {
            if (requestedAttributes.isEmpty()) {
                logger.info("Got request to create variant for path: $treePath and entryId: $entryId but no specified variants")
                throw IllegalArgumentException("Job must contain requested image attributes")
            }
            val original =
                assetRepository.fetchByPath(treePath, entryId, RequestedImageAttributes.ORIGINAL_VARIANT)
                    ?: throw IllegalStateException("No asset found for: $treePath and entryId: $entryId")

            val originalVariant = original.getOriginalVariant()
            val found = objectStore.exists(originalVariant.objectStoreBucket, originalVariant.objectStoreKey)
            if (!found) {
                throw IllegalStateException(
                    "Cannot locate object with bucket: ${originalVariant.objectStoreBucket} key: ${originalVariant.objectStoreKey}",
                )
            }

            var asset: Asset? = null
            val variants = mutableListOf<AssetVariant>()
            requestedAttributes.map { request ->
                val originalVariantChannel = ByteChannel(true)
                val fetchOriginalVariantJob =
                    launch {
                        objectStore.fetch(originalVariant.objectStoreBucket, originalVariant.objectStoreKey, originalVariantChannel)
                    }
                val processedAssetChannel = ByteChannel(true)
                val persistResult =
                    async {
                        objectStore.persist(pathConfiguration.s3Properties.bucket, processedAssetChannel)
                    }
                val newVariant =
                    imageProcessor.generateVariant(
                        source = AssetStreamContainer(originalVariantChannel),
                        requestedAttributes = request,
                        originalVariant = originalVariant,
                        outputChannel = processedAssetChannel,
                        pathConfiguration = pathConfiguration,
                    )
                fetchOriginalVariantJob.join()

                val assetAndVariant =
                    assetRepository.storeVariant(
                        StoreAssetVariantDto(
                            path = original.asset.path,
                            entryId = original.asset.entryId,
                            persistResult = persistResult.await(),
                            imageAttributes = newVariant.attributes,
                            lqips = newVariant.lqip,
                        ),
                    )
                if (asset == null) {
                    asset = assetAndVariant.asset
                }
                variants.addAll(assetAndVariant.variants)
            }
            AssetAndVariants(
                asset = requireNotNull(asset),
                variants = variants,
            )
        }
}
