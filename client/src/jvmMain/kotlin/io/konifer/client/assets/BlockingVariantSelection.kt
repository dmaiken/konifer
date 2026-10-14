package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.AssetContent
import io.konifer.client.assets.fetch.AssetContentMetadata
import io.konifer.client.assets.fetch.ContentDelivery
import io.konifer.common.http.AssetLinkResponse
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.OutputStream

/** Blocking counterpart to [VariantSelection]. */
class BlockingVariantSelection internal constructor(
    private val selection: VariantSelection,
) {
    /** Fetches the selected image through Konifer and buffers it in memory. */
    fun fetchContent(): KoniferResult<AssetContent> = runBlocking { selection.fetchContent() }

    /** Fetches bytes and metadata using [delivery]. Metadata is unavailable for [ContentDelivery.FOLLOW_REDIRECT]. */
    fun fetchContent(delivery: ContentDelivery): KoniferResult<AssetContent> = runBlocking { selection.fetchContent(delivery) }

    /**
     * Streams content through Konifer into the caller-owned [output].
     * Never flushes or closes [output], including on failure or cancellation. The caller controls its
     * lifetime and may append more content afterward. Partial output is not rolled back.
     */
    fun fetchAndWriteContentTo(output: OutputStream): KoniferResult<AssetContentMetadata> =
        fetchAndWriteContentTo(output, ContentDelivery.THROUGH_KONIFER)

    /**
     * Streams content using [delivery] into the caller-owned [output].
     * Never flushes or closes [output], including on failure or cancellation. The caller controls its
     * lifetime and may append more content afterward. I/O failures, including output-write failures,
     * are returned as [KoniferResult.Failure.Transport]; cancellation and unexpected exceptions
     * are rethrown. Partial output is not rolled back.
     * Returns the response metadata after the transfer; metadata is unavailable for [ContentDelivery.FOLLOW_REDIRECT].
     */
    fun fetchAndWriteContentTo(
        output: OutputStream,
        delivery: ContentDelivery,
    ): KoniferResult<AssetContentMetadata> =
        runBlocking {
            val channel = ByteChannel()
            val copy =
                async(Dispatchers.IO) {
                    try {
                        channel.copyTo(output)
                        KoniferResult.Success(Unit)
                    } catch (failure: IOException) {
                        channel.cancel(failure)
                        // Returning an I/O failure lets the producer finish without cancelling its parent scope.
                        KoniferResult.Failure.Transport(failure)
                    }
                }
            try {
                val result = selection.fetchAndWriteContentTo(channel, delivery)
                if (result is KoniferResult.Success) {
                    when (val copied = copy.await()) {
                        is KoniferResult.Success -> result
                        is KoniferResult.Failure -> copied
                    }
                } else {
                    result
                }
            } finally {
                channel.cancel(null)
                copy.cancel()
            }
        }

    /** Fetches a delivery URL for the selected image. */
    fun fetchLink(): KoniferResult<AssetLinkResponse> = runBlocking { selection.fetchLink() }
}
