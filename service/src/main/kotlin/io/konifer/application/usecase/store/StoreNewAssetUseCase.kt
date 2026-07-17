package io.konifer.application.usecase.store

import io.konifer.application.service.OriginalVariantProcessorPipeline
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.AssetRejectedException
import io.konifer.domain.asset.FormatValidator
import io.konifer.domain.context.RequestContextFactory
import io.konifer.domain.event.AssetReadyEvent
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ContentProcessorResult
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.ObjectStoreKeyFactory
import io.konifer.domain.variant.ProcessingPipeline
import io.konifer.domain.variant.Variant
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

class StoreNewAssetUseCase(
    private val assetStreamContainerFactory: AssetContainerFactory,
    private val formatValidator: FormatValidator,
    private val requestContextFactory: RequestContextFactory,
    private val originalVariantProcessorPipeline: OriginalVariantProcessorPipeline,
    private val objectStore: ObjectStore,
    private val assetRepository: AssetRepository,
    private val eventPublisher: EventPublisher,
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
            val format = formatValidator.deriveValidImageFormat(container.peek(1024))
            val context = requestContextFactory.fromStoreRequest(uriPath, format.mimeType)
            val newAsset =
                Asset.New.fromHttpRequest(
                    path = context.path,
                    request = request,
                )

            container.use { container ->
                val pipeline =
                    originalVariantProcessorPipeline.process(
                        scope = this, // Pass the current scope
                        container = container,
                        context = context,
                        format = format,
                    )

                val attributes = pipeline.awaitAttributesOrRejection()
                val objectStoreKey = ObjectStoreKeyFactory.newKey(attributes.format)
                val uploadedAtDeferred =
                    async {
                        objectStore.persist(
                            bucket = context.pathConfiguration.objectStore.bucket,
                            key = objectStoreKey,
                            channel = pipeline.outputChannel,
                        )
                    }
                val pendingPersisted =
                    newAsset
                        .markPending(
                            originalVariant =
                                Variant.Pending.originalVariant(
                                    assetId = newAsset.id,
                                    attributes = attributes,
                                    objectStoreBucket = context.pathConfiguration.objectStore.bucket,
                                    objectStoreKey = objectStoreKey,
                                    lqip = pipeline.lqips.await() ?: LQIPs.NONE,
                                ),
                            additionalLabels = pipeline.awaitLabelsOrRejection(),
                        ).let { assetRepository.storeNew(it) }

                val uploadedAt = uploadedAtDeferred.await()
                when (val result = pipeline.processDeferred.await()) {
                    is ContentProcessorResult.Success -> Unit
                    is ContentProcessorResult.Rejected -> throw AssetRejectedException(result.violationResponses)
                }

                logger.info("Asset: ${pendingPersisted.descriptor} uploaded at $uploadedAt, marking as ready")
                val readyAsset =
                    pendingPersisted.markReady(uploadedAt).also {
                        assetRepository.markReady(it)
                    }

                return@coroutineScope AssetAndLocation(readyAsset, context.path).also {
                    logger.info("Publishing asset ready event")
                    eventPublisher.publish(
                        AssetReadyEvent(
                            pathConfiguration = context.pathConfiguration,
                            originalVariantFile = pipeline.eagerVariantFile,
                            originalVariant = readyAsset.variants.first(),
                        ),
                    )
                }
            }
        }

    private suspend fun ProcessingPipeline.awaitAttributesOrRejection(): Attributes =
        select {
            attributes.onAwait { attributes ->
                attributes
            }
            processDeferred.onAwait { result ->
                when (result) {
                    is ContentProcessorResult.Success -> attributes.await()
                    is ContentProcessorResult.Rejected -> throw AssetRejectedException(result.violationResponses)
                }
            }
        }

    private suspend fun ProcessingPipeline.awaitLabelsOrRejection(): AssetLabels =
        when (val result = processDeferred.await()) {
            is ContentProcessorResult.Success -> result.labels
            is ContentProcessorResult.Rejected -> throw AssetRejectedException(result.violationResponses)
        }
}
