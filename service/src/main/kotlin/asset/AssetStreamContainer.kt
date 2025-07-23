package io.asset

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AssetStreamContainer(
    private val channel: ByteChannel,
) {
    companion object Factory {
        fun fromReadChannel(
            scope: CoroutineScope,
            readChannel: ByteReadChannel,
        ): AssetStreamContainer {
            val byteChannel = ByteChannel(autoFlush = true)
            scope.launch {
                try {
                    readChannel.copyTo(byteChannel)
                } finally {
                    byteChannel.close()
                }
            }

            return AssetStreamContainer(byteChannel)
        }
    }

    private var headerOffset = 0
    private var headerBytes = ByteArray(0)

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
            val read = channel.readAvailable(result, offset, n - offset)
            if (read == -1) break
            offset += read
        }

        return result.copyOf(offset).also {
            if (bufferBytes) {
                headerBytes = headerBytes + it
            }
        }
    }

    suspend fun readAll(): ByteArray {
        return headerBytes + channel.toByteArray()
    }
}
