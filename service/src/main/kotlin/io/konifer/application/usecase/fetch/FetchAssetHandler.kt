package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.FormatValidator
import io.konifer.domain.context.QueryRequestContext
import io.konifer.domain.event.VariantAccessedEvent
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.EventPublisher
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.TransformationNormalizer
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantGenerationResult
import io.konifer.domain.variant.VariantService
import io.konifer.domain.variant.VariantSpecification
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.io.path.deleteIfExists

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantService: VariantService,
    private val assetUrlGenerator: AssetUrlGenerator,
    private val eventPublisher: EventPublisher,
    private val formatValidator: FormatValidator,
    private val transformationNormalizer: TransformationNormalizer,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchRedirectByPath(context: QueryRequestContext): VariantRedirect? {
        val (asset, cacheResult) = fetchMetadataByPath(context, true) ?: return null
        val variant = asset.variants.first()

        val url =
            assetUrlGenerator.generateDeliveryUrl(
                assetData = asset,
                request = context.request,
                pathConfiguration = context.pathConfiguration,
            )
        return VariantRedirect(
            deliveryUrl = url,
            asset = asset,
            cacheResult = cacheResult,
            variant = variant,
        )
    }

    suspend fun fetchLinkByPath(context: QueryRequestContext): VariantLink? {
        val (asset, cacheResult) = fetchMetadataByPath(context, true) ?: return null
        val variant = asset.variants.first()

        return VariantLink(
            path = asset.path,
            entryId = asset.entryId,
            alt = asset.alt,
            lqip = variant.lqips,
            cacheResult = cacheResult,
            attributes = variant.attributes,
            deliveryUrl =
                assetUrlGenerator.generateDeliveryUrl(
                    assetData = asset,
                    request = context.request,
                    pathConfiguration = context.pathConfiguration,
                ),
        )
    }

    suspend fun fetchMetadataAtPath(context: QueryRequestContext): List<AssetData> {
        logger.info("Fetching asset info at path: ${context.path}")
        return assetRepository.fetchAllByPath(
            path = context.path,
            specification = null,
            labels = context.labels,
            order = context.selectors.order,
            limit = context.selectors.limit,
        )
    }

    suspend fun fetchMetadataByPath(
        context: QueryRequestContext,
        generateVariant: Boolean,
    ): AssetInformation? {
        logger.info("Fetching asset info by path: ${context.path}")
        val assetData =
            assetRepository.fetchByPath(
                path = context.path,
                entryId = context.selectors.entryId,
                specification = if (generateVariant && context.requestedTransformation == null) VariantSpecification.Original else null,
                order = context.selectors.order,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetInformation(assetData, CacheResult.HIT)
        }

        val originalVariant =
            checkNotNull(assetData.variants.firstOrNull { it.isOriginalVariant }) {
                "Original variant missing for ready asset: ${assetData.id.value}"
            }
        val specification =
            context.requestedTransformation?.let { requested ->
                val transformation =
                    transformationNormalizer.normalize(
                        requested = requested,
                        originalVariantAttributes = originalVariant.attributes,
                        limits = context.pathConfiguration.transform.limits,
                    )
                VariantSpecification.Transformed(transformation)
            } ?: VariantSpecification.Original
        val matched =
            assetData.variants.firstOrNull {
                specification.matches(
                    stored = it.specification,
                    attributes = it.attributes,
                )
            }
        val variant =
            matched ?: run {
                check(specification is VariantSpecification.Transformed) { "Cannot generate a missing original variant" }
                when (val generated = createOnDemandVariant(assetData.id, originalVariant, specification.transformation, context)) {
                    is VariantGenerationResult.Generated -> generated.variant
                    VariantGenerationResult.AlreadyExists -> assetRepository.fetchVariant(assetData.id, specification) ?: return null
                }
            }
        val cacheResult = matched?.let { CacheResult.HIT } ?: CacheResult.MISS
        return AssetInformation(assetData.copy(variants = listOf(variant)), cacheResult)
            .also { publishVariantAccessedEvent(it.asset) }
    }

    suspend fun fetchContent(
        bucket: String,
        storeKey: String,
        channel: ByteWriteChannel,
    ): Long =
        objectStore
            .fetch(bucket, storeKey, channel)
            .takeIf { it.found }
            ?.contentLength
            ?: throw IllegalStateException("Asset not found in object store: $bucket/$storeKey")

    private suspend fun createOnDemandVariant(
        assetId: AssetId,
        originalVariant: VariantData,
        transformation: Transformation,
        context: QueryRequestContext,
    ): VariantGenerationResult =
        coroutineScope {
            formatValidator.validateOutputFormat(
                allowedFormats = context.pathConfiguration.allowedFormats,
                format = transformation.format,
            )
            val originalVariantFile =
                TemporaryFileFactory.createOriginalVariantTempFile(
                    extension = originalVariant.attributes.format.extension,
                )
            try {
                val channel = ByteChannel()

                val fetchJob =
                    launch {
                        try {
                            objectStore.fetch(
                                bucket = originalVariant.objectStoreBucket,
                                key = originalVariant.objectStoreKey,
                                channel = channel,
                            )
                        } finally {
                            channel.close()
                        }
                    }
                channel.copyAndClose(originalVariantFile.toFile().writeChannel())
                fetchJob.join()
                variantService.generateOnDemandVariant(
                    originalVariantFile = originalVariantFile,
                    originalVariantAttributes = originalVariant.attributes,
                    transformation = transformation,
                    assetId = assetId,
                    originalVariantLQIPs = originalVariant.lqips,
                    pathConfiguration = context.pathConfiguration,
                )
            } finally {
                originalVariantFile.deleteIfExists()
            }
        }

    private suspend fun publishVariantAccessedEvent(assetData: AssetData) {
        eventPublisher.publish(
            VariantAccessedEvent(
                variantId = assetData.variants.first().id,
                path = assetData.path,
                accessedAt = Instant.now(),
            ),
        )
    }
}
