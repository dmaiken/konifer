package io.asset.handler

import io.asset.context.ContentTypeNotPermittedException
import io.asset.context.QueryRequestContext
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
        val variant = assetAndCacheStatus.first.variants.first()

        return AssetLinkDto(
            url = objectStore.generateObjectUrl(variant),
            cacheHit = assetAndCacheStatus.second,
            lqip = variant.lqip,
        )
    }

    suspend fun fetchAssetMetadataByPath(
        context: QueryRequestContext,
        generateVariant: Boolean,
    ): Pair<AssetAndVariants, Boolean>? {
        val entryId = context.modifiers.entryId
        logger.info(
            "Fetching asset info by path: ${context.path} with transformation: ${context.transformation} and labels: ${context.labels}",
        )

        val assetAndVariants =
            assetRepository.fetchByPath(context.path, entryId, context.transformation, context.labels) ?: return null
        if (!generateVariant) {
            return Pair(assetAndVariants, true)
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
            return Pair(deferred.await(), false)
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: $entryId")
            Pair(assetAndVariants, true)
        }
    }

    suspend fun fetchAssetMetadataInPath(context: QueryRequestContext): List<AssetAndVariants> {
        logger.info("Fetching asset info in path: ${context.path}")
        return assetRepository.fetchAllByPath(context.path, null)
    }

    suspend fun fetchAssetContent(
        bucket: String,
        storeKey: String,
        stream: ByteWriteChannel,
    ): Long {
        return objectStore.fetch(bucket, storeKey, stream)
            .takeIf { it.found }?.contentLength
            ?: throw IllegalStateException("Asset not found in object store: $bucket/$storeKey")
    }
}
