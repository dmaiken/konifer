package io.direkt.infrastructure.vips

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import java.io.OutputStream

class ByteChannelOutputStream(
    private val channel: ByteChannel,
) : OutputStream() {
    override fun write(b: Int) {
        runBlocking {
            channel.writeByte(b.toByte())
        }
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        runBlocking {
            channel.writeFully(b, off, len)
        }
    }

    override fun flush() {
        runBlocking {
            channel.flush()
        }
    }

    override fun close() {
        runBlocking {
            channel.flushAndClose()
        }
    }
}
