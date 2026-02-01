package io.konifer.domain.asset

import io.konifer.service.TemporaryFileFactory.createUploadTempFile
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
import kotlin.io.path.pathString

/**
 * 100MB
 */
const val MAX_BYTES_DEFAULT = (1024 * 1024 * 100).toLong()

class AssetDataContainer(
    private val channel: ByteReadChannel,
    private val maxBytes: Long = MAX_BYTES_DEFAULT,
) : AutoCloseable {
    companion object {
        private const val TOO_LARGE_MESSAGE = "Asset exceeds the maximum allowed size"
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)
    private var tempFile: Path? = null

    /**
     * Is the backing channel dumped to a file. If so, then the channel will be closed for reading.
     */
    var isDumpedToFile = false

    fun getTemporaryFile(): Path = tempFile ?: throw IllegalStateException("Temporary file is not initialized!")

    suspend fun toTemporaryFile(extension: String) =
        withContext(Dispatchers.IO) {
            tempFile = createUploadTempFile(extension)
            runCatching {
                FileChannel
                    .open(
                        tempFile,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW,
                    ).use { fileChannel ->
                        val bytesWritten =
                            channel.copyTo(
                                channel = fileChannel,
                                limit = maxBytes + 1,
                            )
                        if (bytesWritten > maxBytes) {
                            throw IllegalArgumentException(TOO_LARGE_MESSAGE)
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

    suspend fun peek(n: Int): ByteArray = channel.peek(n)?.toByteArray() ?: ByteArray(0)

    /**
     * If a temporary file is created, delete it. If the delegate channel is still open, then cancel is.
     */
    override fun close() {
        if (tempFile != null) {
            logger.info("Deleting temporary file: ${tempFile?.pathString}")
            tempFile?.toFile()?.delete()
        }
        if (!channel.isClosedForRead) channel.cancel(null)
    }
}
