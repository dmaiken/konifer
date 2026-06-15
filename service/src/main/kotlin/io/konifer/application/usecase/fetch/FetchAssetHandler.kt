package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.context.ContentTypeNotPermittedException
import io.konifer.domain.context.QueryRequestContext
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.VariantService
import io.konifer.infrastructure.TemporaryFileFactory
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.io.path.deleteIfExists

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantService: VariantService,
    private val assetUrlGenerator: AssetUrlGenerator,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchRedirectByPath(context: QueryRequestContext): VariantRedirect? {
        val (asset, cacheHit) = fetchMetadataByPath(context, true) ?: return null
        val variant = asset.variants.first()

        val url =
            objectStore.generateObjectUrl(
                bucket = variant.objectStoreBucket,
                key = variant.objectStoreKey,
                properties = context.pathConfiguration.returnFormat.redirect,
            )
        return VariantRedirect(
            url = url,
            asset = asset,
            cacheHit = cacheHit,
            variant = variant,
        )
    }

    suspend fun fetchLinkByPath(context: QueryRequestContext): VariantLink? {
        val (asset, cacheHit) = fetchMetadataByPath(context, true) ?: return null
        val variant = asset.variants.first()

        return VariantLink(
            path = asset.path,
            entryId = asset.entryId,
            alt = asset.alt,
            lqip = variant.lqips,
            cacheHit = cacheHit,
            url = assetUrlGenerator.generateAbsoluteContentUrl(asset.path, asset.entryId, context.request.parameters),
        )
    }

    suspend fun fetchMetadataAtPath(context: QueryRequestContext): List<AssetData> {
        logger.info("Fetching asset info at path: ${context.path}")
        return assetRepository.fetchAllByPath(
            path = context.path,
            transformation = null,
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
                transformation = context.transformation,
                order = context.selectors.order,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetInformation(assetData, true)
        }

        return if (assetData.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: ${context.path}, entryId: ${context.selectors.entryId}")

            createOnDemandVariant(
                assetId = assetData.id,
                context = context,
            )

            AssetInformation(
                asset =
                    assetRepository.fetchByPath(
                        path = context.path,
                        entryId = context.selectors.entryId,
                        transformation = context.transformation,
                        order = context.selectors.order,
                        labels = context.labels,
                    ) ?: return null,
                cacheHit = false,
            )
        } else {
            logger.info("Variant found for asset with path: ${context.path}, entryId: ${context.selectors.entryId}")
            AssetInformation(assetData, true)
        }
    }

    suspend fun fetchContent(
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
        context: QueryRequestContext,
    ): Unit =
        coroutineScope {
            context.pathConfiguration.allowedContentTypes?.let {
                if (!it.contains(checkNotNull(context.transformation).format.mimeType)) {
                    throw ContentTypeNotPermittedException("Content type: ${context.transformation.format} not permitted")
                }
            }
            val originalVariant =
                assetRepository
                    .fetchByPath(
                        path = context.path,
                        entryId = context.selectors.entryId,
                        transformation = Transformation.ORIGINAL_VARIANT,
                        order = context.selectors.order,
                        labels = context.labels,
                    )?.variants
                    ?.first { it.isOriginalVariant }
                    ?: return@coroutineScope
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
                    transformation = checkNotNull(context.transformation),
                    assetId = assetId,
                    lqipImplementations = context.pathConfiguration.image.previews,
                    originalVariantLQIPs = originalVariant.lqips,
                    bucket = context.pathConfiguration.objectStore.bucket,
                    expiresAt =
                        context.pathConfiguration.transform.expire
                            .expiresAt(),
                )
            } finally {
                originalVariantFile.deleteIfExists()
            }
        }
}
