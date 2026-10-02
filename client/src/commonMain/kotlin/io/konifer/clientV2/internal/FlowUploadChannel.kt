package io.konifer.clientV2.internal

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal fun flowUploadChannel(
    scope: CoroutineScope,
    chunks: () -> Flow<ByteArray>,
): ByteReadChannel {
    val channel = ByteChannel(autoFlush = true)
    scope.launch {
        try {
            chunks().collect { channel.writeFully(it) }
            channel.close()
        } catch (e: CancellationException) {
            channel.cancel(e)
            throw e
        } catch (e: Throwable) {
            channel.cancel(e)
        }
    }
    return channel
}
