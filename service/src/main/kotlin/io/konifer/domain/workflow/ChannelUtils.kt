package io.konifer.domain.workflow

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

// A helper function to multiplex one stream into two
suspend fun teeStream(
    source: ByteReadChannel,
    firstChannel: ByteWriteChannel,
    secondChannel: ByteWriteChannel?, // Pass null if no eager variants
) {
    val buffer = ByteBuffer.allocate(8192)

    try {
        while (!source.isClosedForRead) {
            buffer.clear()
            val bytesRead = source.readAvailable(buffer)
            if (bytesRead <= 0) break

            buffer.flip()

            // If we have eager variants, we write to BOTH
            if (secondChannel != null) {
                // We must duplicate the buffer's read pointers so both channels can read it
                val s3Buffer = buffer.duplicate()
                val fileBuffer = buffer.duplicate()

                // Using coroutineScope to write to both concurrently
                coroutineScope {
                    launch { firstChannel.writeFully(s3Buffer) }
                    launch { secondChannel.writeFully(fileBuffer) }
                }
            } else {
                // No eager variants, pure S3 pipeline
                firstChannel.writeFully(buffer)
            }
        }
    } finally {
        // Ensure downstream channels know the stream is done
        firstChannel.flushAndClose()
        secondChannel?.flushAndClose()
    }
}
