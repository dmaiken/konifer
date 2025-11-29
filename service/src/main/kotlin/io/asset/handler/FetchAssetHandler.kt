package io.asset.handler

import io.asset.context.ContentTypeNotPermittedException
import io.asset.context.QueryRequestContext
import io.asset.handler.dto.AssetLinkDto
import io.asset.handler.dto.AssetMetadataDto
import io.asset.model.AssetAndVariants
import io.asset.repository.AssetRepository
import io.asset.store.ObjectStore
import io.asset.variant.generation.ImageProcessingJob
import io.asset.variant.generation.PriorityChannelScheduler
import io.asset.variant.generation.VariantGenerationJob
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CompletableDeferred

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantJobScheduler: PriorityChannelScheduler<ImageProcessingJob<*>>,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchAssetLinkByPath(context: QueryRequestContext): AssetLinkDto? {
        val assetAndCacheStatus = fetchAssetMetadataByPath(context, true) ?: return null
        logger.info("Found asset with response: $assetAndCacheStatus and route: ${context.path}")
        val variant = assetAndCacheStatus.asset.variants.first()

        return AssetLinkDto(
            url = objectStore.generateObjectUrl(variant),
            cacheHit = assetAndCacheStatus.cacheHit,
            lqip = variant.lqip,
        )
    }

    suspend fun fetchAssetMetadataInPath(context: QueryRequestContext): List<AssetAndVariants> {
        logger.info("Fetching asset info in path: ${context.path}")
        return assetRepository.fetchAllByPath(
            path = context.path,
            transformation = null,
            orderBy = context.modifiers.orderBy,
            limit = context.modifiers.limit,
        )
    }

    suspend fun fetchAssetMetadataByPath(
        context: QueryRequestContext,
        generateVariant: Boolean,
    ): AssetMetadataDto? {
        val entryId = context.modifiers.entryId
        logger.info(
            "Fetching asset info by path: ${context.path} with transformation: ${context.transformation} and labels: ${context.labels}",
        )

        val assetAndVariants =
            assetRepository.fetchByPath(
                path = context.path,
                entryId,
                transformation = context.transformation,
                orderBy = context.modifiers.orderBy,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetMetadataDto(assetAndVariants, true)
        }

        return if (assetAndVariants.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: $entryId")
            context.pathConfiguration.allowedContentTypes?.let {
                if (!it.contains(checkNotNull(context.transformation).format.mimeType)) {
                    throw ContentTypeNotPermittedException("Content type: ${context.transformation.format} not permitted")
                }
            }

            val deferred = CompletableDeferred<AssetAndVariants>()
            variantJobScheduler.scheduleSynchronousJob(
                VariantGenerationJob(
                    treePath = assetAndVariants.asset.path,
                    entryId = assetAndVariants.asset.entryId,
                    pathConfiguration = context.pathConfiguration,
                    transformations = listOf(checkNotNull(context.transformation)),
                    deferredResult = deferred,
                ),
            )
            AssetMetadataDto(deferred.await(), false)
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: $entryId")
            AssetMetadataDto(assetAndVariants, true)
        }
    }

    suspend fun fetchAssetContent(
        bucket: String,
        storeKey: String,
        stream: ByteWriteChannel,
    ): Long =
        objectStore
            .fetch(bucket, storeKey, stream)
            .takeIf { it.found }
            ?.contentLength
            ?: throw IllegalStateException("Asset not found in object store: $bucket/$storeKey")
}
