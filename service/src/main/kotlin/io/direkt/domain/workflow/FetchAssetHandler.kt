package io.direkt.domain.workflow

import io.direkt.domain.asset.AssetData
import io.direkt.domain.asset.AssetId
import io.direkt.domain.asset.AssetMetadata
import io.direkt.domain.ports.AssetRepository
import io.direkt.domain.ports.ObjectRepository
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.VariantLink
import io.direkt.service.TemporaryFileFactory
import io.direkt.service.context.AssetQueryRequestContext
import io.direkt.service.context.ContentTypeNotPermittedException
import io.direkt.service.variant.VariantService
import io.ktor.util.cio.use
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectRepository,
    private val variantService: VariantService,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchAssetLinkByPath(context: AssetQueryRequestContext): VariantLink? {
        val assetAndCacheStatus = fetchAssetMetadataByPath(context, true) ?: return null
        logger.info("Found asset with response: $assetAndCacheStatus and route: ${context.path}")
        val variant = assetAndCacheStatus.asset.variants.first()

        return VariantLink(
            url = objectStore.generateObjectUrl(variant.objectStoreBucket, variant.objectStoreKey),
            cacheHit = assetAndCacheStatus.cacheHit,
            lqip = variant.lqips,
        )
    }

    suspend fun fetchAssetMetadataAtPath(context: AssetQueryRequestContext): List<AssetData> {
        logger.info("Fetching asset info at path: ${context.path}")
        return assetRepository.fetchAllByPath(
            path = context.path,
            transformation = null,
            orderBy = context.modifiers.orderBy,
            limit = context.modifiers.limit,
        )
    }

    suspend fun fetchAssetMetadataByPath(
        context: AssetQueryRequestContext,
        generateVariant: Boolean,
    ): AssetMetadata? {
        logger.info(
            "Fetching asset metadata by path: ${context.path} with transformation: ${context.transformation} and labels: ${context.labels}",
        )

        val assetData =
            assetRepository.fetchByPath(
                path = context.path,
                entryId = context.modifiers.entryId,
                transformation = context.transformation,
                orderBy = context.modifiers.orderBy,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetMetadata(assetData, true)
        }

        return if (assetData.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: ${context.modifiers.entryId}")

            createOnDemandVariant(
                assetId = assetData.id,
                context = context,
            )

            AssetMetadata(
                asset =
                    assetRepository.fetchByPath(
                        path = context.path,
                        entryId = context.modifiers.entryId,
                        transformation = context.transformation,
                        orderBy = context.modifiers.orderBy,
                        labels = context.labels,
                    ) ?: return null,
                cacheHit = false,
            )
        } else {
            logger.info("Variant found for asset with path: ${context.path} and entryId: ${context.modifiers.entryId}")
            AssetMetadata(assetData, true)
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

    private suspend fun createOnDemandVariant(
        assetId: AssetId,
        context: AssetQueryRequestContext,
    ) {
        context.pathConfiguration.allowedContentTypes?.let {
            if (!it.contains(checkNotNull(context.transformation).format.mimeType)) {
                throw ContentTypeNotPermittedException("Content type: ${context.transformation.format} not permitted")
            }
        }
        val originalVariant =
            assetRepository
                .fetchByPath(
                    path = context.path,
                    entryId = context.modifiers.entryId,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    orderBy = context.modifiers.orderBy,
                    labels = context.labels,
                )?.variants
                ?.first { it.isOriginalVariant }
                ?: return
        val originalVariantFile =
            TemporaryFileFactory.createOriginalVariantTempFile(
                extension = originalVariant.attributes.format.extension,
            )
        try {
            val fileChannel =
                withContext(Dispatchers.IO) {
                    originalVariantFile.toFile().writeChannel()
                }
            fileChannel.use {
                objectStore.fetch(
                    bucket = originalVariant.objectStoreBucket,
                    key = originalVariant.objectStoreKey,
                    channel = fileChannel,
                )
            }
            variantService.generateOnDemandVariant(
                originalVariantFile = originalVariantFile,
                transformation = checkNotNull(context.transformation),
                assetId = assetId,
                lqipImplementations = context.pathConfiguration.image.previews,
                originalVariantLQIPs = originalVariant.lqips,
                bucket = context.pathConfiguration.s3Path.bucket,
            )
        } finally {
            withContext(Dispatchers.IO) {
                originalVariantFile.toFile().delete()
            }
        }
    }
}
