package io.konifer.domain.workflow

import io.konifer.domain.asset.AssetData
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.AssetMetadata
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.variant.Transformation
import io.konifer.domain.variant.VariantLink
import io.konifer.domain.variant.VariantRedirect
import io.konifer.infrastructure.HttpProperties
import io.konifer.service.TemporaryFileFactory
import io.konifer.service.context.ContentTypeNotPermittedException
import io.konifer.service.context.QueryRequestContext
import io.konifer.service.context.RequestContextFactory.Companion.PATH_NAMESPACE_SEPARATOR
import io.konifer.service.variant.VariantService
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.util.cio.use
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val variantService: VariantService,
    private val httpProperties: HttpProperties,
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
            url = constructContentUrl(asset.path, asset.entryId, context.request.parameters),
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
    ): AssetMetadata? {
        logger.info(
            "Fetching asset metadata by path: ${context.path} with transformation: ${context.transformation} and labels: ${context.labels}",
        )

        val assetData =
            assetRepository.fetchByPath(
                path = context.path,
                entryId = context.selectors.entryId,
                transformation = context.transformation,
                order = context.selectors.order,
                labels = context.labels,
            ) ?: return null
        if (!generateVariant) {
            return AssetMetadata(assetData, true)
        }

        return if (assetData.variants.isEmpty()) {
            logger.info("Generating variant of asset with path: ${context.path} and entryId: ${context.selectors.entryId}")

            createOnDemandVariant(
                assetId = assetData.id,
                context = context,
            )

            AssetMetadata(
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
            logger.info("Variant found for asset with path: ${context.path} and entryId: ${context.selectors.entryId}")
            AssetMetadata(assetData, true)
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
                    entryId = context.selectors.entryId,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    order = context.selectors.order,
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
                bucket = context.pathConfiguration.objectStore.bucket,
            )
        } finally {
            withContext(Dispatchers.IO) {
                originalVariantFile.toFile().delete()
            }
        }
    }

    private fun constructContentUrl(
        path: String,
        entryId: Long,
        parameters: Parameters,
    ): String =
        URLBuilder(httpProperties.publicUrl)
            .apply {
                appendPathSegments("assets", path.removePrefix("/"), PATH_NAMESPACE_SEPARATOR, "entry", entryId.toString(), "content")
                this.parameters.appendAll(parameters)
            }.build()
            .toString()
}
