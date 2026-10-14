package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.AssetContent
import io.konifer.client.assets.fetch.AssetContentMetadata
import io.konifer.client.assets.fetch.ContentDelivery
import io.konifer.client.assets.fetch.RequestedTransformation
import io.konifer.client.assets.fetch.fetchAssetContent
import io.konifer.client.assets.fetch.fetchAssetContentTo
import io.konifer.client.assets.fetch.fetchAssetLink
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.common.http.AssetLinkResponse
import io.ktor.utils.io.ByteWriteChannel

/** Selects original or transformed image content for an [AssetSelection]. */
class VariantSelection internal constructor(
    private val infra: RequestInfrastructure,
    private val asset: AssetSelection,
    private val requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
) {
    /**
     * Streams content through Konifer and completes [destination]'s write side.
     * The destination is flushed and closed on success, or terminated exceptionally on failure or
     * cancellation. It cannot be reused. Consume it concurrently to avoid blocking on backpressure.
     * Partial output is not rolled back; coroutine cancellation is rethrown.
     */
    suspend fun fetchAndWriteContentTo(destination: ByteWriteChannel): KoniferResult<AssetContentMetadata> =
        fetchAndWriteContentTo(destination, ContentDelivery.THROUGH_KONIFER)

    /**
     * Streams content using [delivery] and completes [destination]'s write side.
     * The destination is flushed and closed on success, or terminated exceptionally on failure or
     * cancellation. It cannot be reused. Consume it concurrently to avoid blocking on backpressure.
     * I/O failures are returned as [KoniferResult.Failure.Transport]; coroutine cancellation and
     * unexpected exceptions are rethrown. Partial output is not rolled back.
     * Returns the response metadata after the transfer; metadata is unavailable for [ContentDelivery.FOLLOW_REDIRECT].
     */
    suspend fun fetchAndWriteContentTo(
        destination: ByteWriteChannel,
        delivery: ContentDelivery,
    ): KoniferResult<AssetContentMetadata> = fetchAssetContentTo(infra, asset, requestedTransformation, destination, delivery)

    /** Fetches the selected image through Konifer and buffers it in memory. */
    suspend fun fetchContent(): KoniferResult<AssetContent> = fetchContent(ContentDelivery.THROUGH_KONIFER)

    /** Fetches bytes and metadata using [delivery]. Metadata is unavailable for [ContentDelivery.FOLLOW_REDIRECT]. */
    suspend fun fetchContent(delivery: ContentDelivery): KoniferResult<AssetContent> =
        fetchAssetContent(infra, asset, requestedTransformation, delivery)

    // link and redirect modes return the same delivery URL, so I am not exposing a redirect() method

    /** Fetches a delivery URL for the selected image. */
    suspend fun fetchLink(): KoniferResult<AssetLinkResponse> = fetchAssetLink(infra, asset, requestedTransformation)
}
