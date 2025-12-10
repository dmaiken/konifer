package io.direkt.domain.workflows

import io.direkt.asset.handler.dto.AssetLinkDto
import io.direkt.asset.handler.dto.AssetMetadataDto
import io.direkt.domain.asset.AssetData
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.ports.VariantGenerator
import io.direkt.service.context.ContentTypeNotPermittedException
import io.direkt.service.context.QueryRequestContext
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectRepository,
    private val variantGenerator: VariantGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchAssetLinkByPath(context: QueryRequestContext): AssetLinkDto? {
        val assetAndCacheStatus = fetchAssetMetadataByPath(context, true) ?: return null
        logger.info("Found asset with response: $assetAndCacheStatus and route: ${context.path}")
        val variant = assetAndCacheStatus.asset.variants.first()

        return AssetLinkDto(
            url = objectStore.generateObjectUrl(variant.objectStoreBucket, variant.objectStoreKey),
            cacheHit = assetAndCacheStatus.cacheHit,
            lqip = variant.lqips,
        )
    }

    suspend fun fetchAssetMetadataInPath(context: QueryRequestContext): List<AssetData> {
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

        val assetData =
            assetRepository.fetchByPath(
                path = context.path,
                entryId = entryId,
                transformation = context.transformation,
                orderBy = context.modifiers.orderBy,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetMetadataDto(assetData, true)
        }

        return if (assetData.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: $entryId")
            context.pathConfiguration.allowedContentTypes?.let {
                if (!it.contains(checkNotNull(context.transformation).format.mimeType)) {
                    throw ContentTypeNotPermittedException("Content type: ${context.transformation.format} not permitted")
                }
            }

            variantGenerator
                .generateOnDemandVariant(
                    path = assetData.path,
                    entryId = assetData.entryId,
                    lqipImplementations = context.pathConfiguration.imageProperties.previews,
                    bucket = context.pathConfiguration.s3PathProperties.bucket,
                    transformation = checkNotNull(context.transformation),
                ).await()
            AssetMetadataDto(
                asset =
                    assetRepository.fetchByPath(
                        path = context.path,
                        entryId = entryId,
                        transformation = context.transformation,
                        orderBy = context.modifiers.orderBy,
                        labels = context.labels,
                    ) ?: return null,
                cacheHit = false,
            )
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: $entryId")
            AssetMetadataDto(assetData, true)
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
