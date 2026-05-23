package io.konifer.infrastructure

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * A helper function to multiplex one stream into two. If the [secondChannel] is not provided, the stream
 * is written to only the [firstChannel].
 */
suspend fun teeStream(
    source: ByteReadChannel,
    firstChannel: ByteWriteChannel,
    secondChannel: ByteWriteChannel?,
) {
    val buffer = ByteBuffer.allocate(8192)

    try {
        while (!source.isClosedForRead) {
            buffer.clear()
            val bytesRead = source.readAvailable(buffer)
            if (bytesRead <= 0) break

            buffer.flip()

            if (secondChannel != null) {
                // We must duplicate the buffer's read pointers so both channels can read it
                val firstChannelBuffer = buffer.duplicate()
                val secondChannelBuffer = buffer.duplicate()

                coroutineScope {
                    launch { firstChannel.writeFully(firstChannelBuffer) }
                    launch { secondChannel.writeFully(secondChannelBuffer) }
                }
            } else {
                firstChannel.writeFully(buffer)
            }
        }
    } finally {
        // Ensure downstream channels know the stream is done
        firstChannel.flushAndClose()
        secondChannel?.flushAndClose()
    }
}
