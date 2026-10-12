package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import io.konifer.common.image.ImageFormat
import io.konifer.domain.transformation.Transformation
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.lang.foreign.Arena
import java.nio.channels.Channels

object VipsJpegXlEncoder : VipsEncoder {
    /**
     * Encodes JPEG XL through a seekable memory target before writing it to the output channel.
     *
     * libvips uses libjxl's output-processor API when built against libjxl 0.9 or newer. That
     * encoder path may seek while producing its output, but vips-ffm's OutputStream-backed custom
     * target only supports sequential writes. A memory target is seekable and keeps the encoded
     * bytes in native memory for the lifetime of [arena].
     */
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
        val target = VTarget.newToMemory(arena)
        source.writeToTarget(target, ImageFormat.JPEG_XL.extension, *options)

        val encoded = target.blob.asArenaScopedByteBuffer()
        Channels.newChannel(outputChannel.toOutputStream()).use { channel ->
            while (encoded.hasRemaining()) {
                channel.write(encoded)
            }
        }
    }
}
