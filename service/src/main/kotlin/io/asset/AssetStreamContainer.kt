package io.asset

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CountedByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.io.IOException

/**
 * 100MB
 */
const val MAX_BYTES_DEFAULT = (1024 * 1024 * 100).toLong()

class AssetStreamContainer(
    channel: ByteReadChannel,
    private val maxBytes: Long = MAX_BYTES_DEFAULT,
) {
    companion object {
        private const val TOO_LARGE_MESSAGE = "Asset exceeds the maximum allowed size"
    }

    private var headerOffset = 0
    private var headerBytes = ByteArray(0)
    private val counterChannel = CountedByteReadChannel(channel)

    /**
     * Reads [n] bytes from the backing channel. If [bufferBytes] is true, then the read bytes are also read into an
     * internal buffer for reuse. Calling this method after buffering the previously read bytes will result in the
     * buffered bytes being returned again.
     */
    suspend fun readNBytes(
        n: Int,
        bufferBytes: Boolean,
    ): ByteArray {
        val result = ByteArray(n)
        var offset = 0

        try {
            // Read from header first
            if (headerOffset < headerBytes.size) {
                val headerRemaining = headerBytes.size - headerOffset
                val toCopy = minOf(n, headerRemaining)
                headerBytes.copyInto(result, destinationOffset = 0, startIndex = headerOffset, endIndex = headerOffset + toCopy)
                headerOffset += toCopy
                offset += toCopy
            }

            // Then from the channel
            while (offset < n) {
                val read = counterChannel.readAvailable(result, offset, n - offset)
                if (read == -1) break
                offset += read
            }

            return result.copyOf(offset).also {
                if (bufferBytes) {
                    headerBytes += it
                }

                if (counterChannel.totalBytesRead > maxBytes) {
                    counterChannel.cancel()
                    throw IllegalArgumentException(TOO_LARGE_MESSAGE)
                }
            }
        } catch (e: IOException) {
            if (e.message == TOO_LARGE_MESSAGE) {
                throw IllegalArgumentException(TOO_LARGE_MESSAGE, e)
            }

            throw e
        }
    }
}
