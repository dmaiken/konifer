package io.direkt.asset

import io.direkt.infrastructure.TemporaryFileFactory.createUploadTempFile
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.CountedByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * 100MB
 */
const val MAX_BYTES_DEFAULT = (1024 * 1024 * 100).toLong()

class AssetDataContainer(
    channel: ByteReadChannel,
    private val maxBytes: Long = MAX_BYTES_DEFAULT,
) : AutoCloseable {
    companion object {
        private const val TOO_LARGE_MESSAGE = "Asset exceeds the maximum allowed size"
    }

    private var bufferOffset = 0
    private var buffer = ByteArrayOutputStream()
    private val counterChannel = CountedByteReadChannel(delegate = channel)
    private var isTooLarge = false
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private var tempFile: File? = null

    /**
     * Is the backing channel dumped to a file. If so, then the channel will be closed for reading.
     */
    var isDumpedToFile = false

    fun getTemporaryFile(): File = tempFile ?: throw IllegalStateException("Temporary file is not initialized!")

    suspend fun toTemporaryFile(extension: String) =
        withContext(Dispatchers.IO) {
            tempFile = createUploadTempFile(extension)
            try {
                FileChannel
                    .open(
                        tempFile?.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    ).use { fileChannel ->
                        var bytesWritten = fileChannel.write(ByteBuffer.wrap(buffer.toByteArray())).toLong()
                        bytesWritten += counterChannel.copyTo(fileChannel)
                        if (bytesWritten > maxBytes) {
                            throw IllegalArgumentException(TOO_LARGE_MESSAGE)
                        }

                        logger.debug { "Successfully wrote $bytesWritten bytes to ${tempFile?.absolutePath}" }
                        isDumpedToFile = true
                    }
            } catch (e: Exception) {
                // If an error occurs during streaming, ensure the incomplete file is deleted.
                tempFile?.delete()
                throw e
            }
        }

    /**
     * Reads [n] bytes from the backing channel. If [peek] is true, then the read bytes are also read into an
     * internal buffer for reuse. Calling this method after buffering the previously read bytes will result in the
     * buffered bytes being returned again.
     */
    suspend fun readNBytes(
        n: Int,
        peek: Boolean,
    ): ByteArray {
        val result = ByteArray(n)
        var offset = 0

        if (isTooLarge) {
            throw IllegalArgumentException(TOO_LARGE_MESSAGE)
        }

        try {
            // Read from buffer first
            if (bufferOffset < buffer.size()) {
                val bufferRemaining = buffer.size() - bufferOffset
                val toCopy = minOf(n, bufferRemaining)
                buffer.toByteArray().copyInto(result, destinationOffset = 0, startIndex = bufferOffset, endIndex = bufferOffset + toCopy)
                bufferOffset += toCopy
                offset += toCopy
            }

            // Then from the channel
            while (offset < n) {
                val read = counterChannel.readAvailable(result, offset, n - offset)
                if (read == -1) break
                offset += read
            }

            return result.copyOf(offset).also {
                if (peek) {
                    buffer.writeBytes(it)
                }

                if (counterChannel.totalBytesRead > maxBytes) {
                    isTooLarge = true
                    throw IllegalArgumentException(TOO_LARGE_MESSAGE)
                }
            }
        } catch (e: CancellationException) {
            // This catches exceptions thrown by Ktor when it tries to read from a cancelled channel.
            // If the size check failed, we re-throw the specific exception that Ktor will map to 400.
            if (isTooLarge) {
                throw IllegalArgumentException(TOO_LARGE_MESSAGE)
            }
            throw e
        } finally {
            if (isTooLarge) {
                // Cancel the underlying channel to stop the client from sending more data
                close()
            }
        }
    }

    /**
     * If a temporary file is created, delete it. If the delegate channel is still open, then cancel is.
     */
    override fun close() {
        if (tempFile != null) {
            logger.info("Deleting temporary file: ${tempFile?.absolutePath}")
            tempFile?.delete()
        }
        if (!counterChannel.isClosedForRead) {
            counterChannel.cancel(null)
        }
    }
}
