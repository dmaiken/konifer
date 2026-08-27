package io.konifer.domain.asset

import io.konifer.infrastructure.TemporaryFileFactory.createUploadTempFile
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.peek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString

class AssetDataTooLargeException(
    maxBytes: Long,
) : IllegalArgumentException("Asset exceeds the maximum allowed size of $maxBytes bytes")

class AssetDataContainer(
    private val channel: ByteReadChannel,
    private val maxBytes: Long = Long.MAX_VALUE,
) : AutoCloseable {
    companion object {
        private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    }

    private var tempFile: Path? = null

    /**
     * Is the backing channel dumped to a file. If so, then the channel will be closed for reading.
     */
    var isDumpedToFile = false

    fun getTemporaryFile(): Path = tempFile ?: throw IllegalStateException("Temporary file is not initialized!")

    suspend fun toTemporaryFile(extension: String) =
        withContext(Dispatchers.IO) {
            if (isDumpedToFile) {
                return@withContext
            }

            tempFile = createUploadTempFile(extension)
            runCatching {
                FileChannel
                    .open(
                        tempFile,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW,
                    ).use { fileChannel ->
                        val bytesWritten =
                            if (maxBytes == Long.MAX_VALUE) {
                                channel.copyTo(fileChannel)
                            } else {
                                channel.copyTo(fileChannel, limit = maxBytes + 1)
                            }
                        if (bytesWritten > maxBytes) {
                            throw AssetDataTooLargeException(maxBytes)
                        }

                        logger.debug { "Successfully wrote $bytesWritten bytes to ${tempFile?.pathString}" }
                        isDumpedToFile = true
                    }
            }.onFailure { e ->
                // If an error occurs during streaming, ensure the incomplete file is deleted.
                tempFile?.toFile()?.delete()
                channel.cancel(e)
            }.getOrThrow()
        }

    suspend fun peek(n: Int): ByteArray =
        tempFile?.let { path ->
            withContext(Dispatchers.IO) {
                path.toFile().inputStream().use { it.readNBytes(n) }
            }
        } ?: channel.peek(n)?.toByteArray() ?: ByteArray(0)

    /**
     * If a temporary file is created, delete it. If the delegate channel is still open, then cancel is.
     */
    override fun close() {
        if (tempFile != null) {
            logger.debug { "Deleting temporary file: ${tempFile?.pathString}" }
            tempFile?.deleteIfExists()
        }
        if (!channel.isClosedForRead) channel.cancel(null)
    }
}
