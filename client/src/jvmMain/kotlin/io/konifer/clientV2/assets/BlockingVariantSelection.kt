package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.ContentDelivery
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
    fun fetchContentBytes(): KoniferV2Result<ByteArray> = runBlocking { selection.fetchContentBytes() }

    /** Fetches the selected image using [delivery] and buffers it in memory. */
    fun fetchContentBytes(delivery: ContentDelivery): KoniferV2Result<ByteArray> = runBlocking { selection.fetchContentBytes(delivery) }

    /**
     * Streams content through Konifer into the caller-owned [output].
     * Never flushes or closes [output], including on failure or cancellation. The caller controls its
     * lifetime and may append more content afterward. Partial output is not rolled back.
     */
    fun fetchAndWriteContentTo(output: OutputStream): KoniferV2Result<Unit> =
        fetchAndWriteContentTo(output, ContentDelivery.THROUGH_KONIFER)

    /**
     * Streams content using [delivery] into the caller-owned [output].
     * Never flushes or closes [output], including on failure or cancellation. The caller controls its
     * lifetime and may append more content afterward. I/O failures, including output-write failures,
     * are returned as [KoniferV2Result.Failure.Transport]; cancellation and unexpected exceptions
     * are rethrown. Partial output is not rolled back.
     */
    fun fetchAndWriteContentTo(
        output: OutputStream,
        delivery: ContentDelivery,
    ): KoniferV2Result<Unit> =
        runBlocking {
            val channel = ByteChannel()
            val copy =
                async(Dispatchers.IO) {
                    try {
                        channel.copyTo(output)
                        KoniferV2Result.Success(Unit)
                    } catch (failure: Throwable) {
                        channel.cancel(failure)
                        // Returning an I/O failure lets the producer finish without cancelling its parent scope.
                        if (failure is IOException) KoniferV2Result.Failure.Transport(failure) else throw failure
                    }
                }
            try {
                val result = selection.fetchAndWriteContentTo(channel, delivery)
                if (result is KoniferV2Result.Success) copy.await() else result
            } finally {
                channel.cancel(null)
                copy.cancel()
            }
        }

    /** Fetches a delivery URL for the selected image. */
    fun fetchLink(): KoniferV2Result<AssetLinkResponse> = runBlocking { selection.fetchLink() }
}
