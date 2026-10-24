package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetService
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.context.QueryRequestContext
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import io.ktor.utils.io.ByteWriteChannel

class FetchAssetHandler(
    private val assetRepository: AssetRepository,
    private val objectStore: ObjectStore,
    private val assetUrlGenerator: AssetUrlGenerator,
    private val assetService: AssetService,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun fetchRedirectByPath(context: QueryRequestContext): VariantRedirect? {
        val (asset, variant, cacheResult, deliveryTransformation) = assetService.fetchForDelivery(context) ?: return null

        val url =
            assetUrlGenerator.generateDeliveryUrl(
                path = asset.path,
                entryId = asset.entryId,
                variant = variant,
                transformation = deliveryTransformation,
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
        val (asset, variant, cacheResult, deliveryTransformation) = assetService.fetchForDelivery(context) ?: return null

        val url =
            assetUrlGenerator.generateDeliveryUrl(
                path = asset.path,
                entryId = asset.entryId,
                variant = variant,
                transformation = deliveryTransformation,
                request = context.request,
                pathConfiguration = context.pathConfiguration,
            )
        return VariantLink(
            path = asset.path,
            entryId = asset.entryId,
            alt = asset.alt,
            lqip = variant.lqips,
            cacheResult = cacheResult,
            attributes = variant.attributes,
            deliveryUrl = url,
        )
    }

    suspend fun fetchMetadataAtPath(context: QueryRequestContext): List<AssetData> {
        logger.debug { "Fetching asset info at path: ${context.path}" }
        return assetRepository.fetchAllByPath(
            path = context.path,
            labels = context.labels,
            order = context.selectors.order,
            limit = context.selectors.limit,
        )
    }

    suspend fun fetchMetadataByPath(context: QueryRequestContext): AssetInformation? {
        logger.debug { "Fetching asset info by path: ${context.path}" }
        val assetData =
            assetRepository.fetchByPath(
                path = context.path,
                entryId = context.selectors.entryId,
                order = context.selectors.order,
                labels = context.labels,
            ) ?: return null
        return AssetInformation(assetData, CacheResult.HIT)
    }

    suspend fun fetchContentByPath(context: QueryRequestContext): AssetContent? {
        val delivery = assetService.fetchForDelivery(context) ?: return null
        val variant = delivery.selectedVariant
        val bucket = variant.objectStoreBucket
        val storeKey = variant.objectStoreKey

        return AssetContent(delivery) { channel ->
            fetchContent(
                bucket = bucket,
                storeKey = storeKey,
                channel = channel,
            )
        }
    }

    private suspend fun fetchContent(
        bucket: String,
        storeKey: String,
        channel: ByteWriteChannel,
    ): Long =
        objectStore
            .fetch(bucket, storeKey, channel)
            .takeIf { it.found }
            ?.contentLength
            ?: throw IllegalStateException("Asset not found in object store: $bucket/$storeKey")
}
