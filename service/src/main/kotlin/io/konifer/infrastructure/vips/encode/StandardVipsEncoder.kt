package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VCustomTarget
import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import java.lang.foreign.Arena

object StandardVipsEncoder : VipsEncoder {
    private const val ERROR_CODE = -1
    private const val SUCCESS_CODE = 0

    override fun writeToStream(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ) {
        val options =
            constructEncoderOptions(
                format = transformation.format,
                quality = transformation.quality.value,
            )

        var callbackFailure: Throwable? = null
        try {
            val target =
                VCustomTarget(
                    arena,
                    { segment ->
                        if (callbackFailure != null) {
                            ERROR_CODE.toLong()
                        } else {
                            try {
                                // Copy into the channel before libvips can reuse the native buffer.
                                runBlocking { outputChannel.writeFully(segment.asByteBuffer()) }
                                segment.byteSize()
                            } catch (cause: Throwable) {
                                // Exceptions must not escape an FFM upcall into native code.
                                callbackFailure = cause
                                ERROR_CODE.toLong()
                            }
                        }
                    },
                    { if (callbackFailure == null) SUCCESS_CODE else ERROR_CODE },
                )
            source.writeToTarget(target, transformation.format.extension, *options)
            callbackFailure?.let { throw it }
            runBlocking { outputChannel.flush() }
            outputChannel.close()
        } catch (cause: Throwable) {
            val failure = callbackFailure ?: cause
            outputChannel.cancel(failure)
            throw failure
        }
    }
}
