package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsForeignHeifEncoder
import app.photofox.vipsffm.enums.VipsForeignSubsample
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.vipsProperties
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ENCODER
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_QUALITY
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_SUBSAMPLE_MODE
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.lang.foreign.Arena
import java.nio.channels.Channels

object VipsEncoder {
    fun writeToStream(
        arena: Arena,
        source: VImage,
        format: ImageFormat,
        quality: Int?,
        outputChannel: ByteChannel,
    ) {
        val options =
            constructEncoderOptions(
                format = format,
                quality = quality,
            )

        if (format == ImageFormat.JPEG_XL) {
            writeJpegXlToStream(arena, source, outputChannel, options)
        } else {
            source.writeToStream(outputChannel.toOutputStream(), format.extension, *options)
        }
    }

    /**
     * Encodes JPEG XL through a seekable memory target before writing it to the output channel.
     *
     * libvips uses libjxl's output-processor API when built against libjxl 0.9 or newer. That
     * encoder path may seek while producing its output, but vips-ffm's OutputStream-backed custom
     * target only supports sequential writes. A memory target is seekable and keeps the encoded
     * bytes in native memory for the lifetime of [arena].
     */
    private fun writeJpegXlToStream(
        arena: Arena,
        source: VImage,
        outputChannel: ByteChannel,
        options: Array<VipsOption>,
    ) {
        val target = VTarget.newToMemory(arena)
        source.writeToTarget(target, ImageFormat.JPEG_XL.extension, *options)

        val encoded = target.blob.asArenaScopedByteBuffer()
        Channels.newChannel(outputChannel.toOutputStream()).use { channel ->
            while (encoded.hasRemaining()) {
                channel.write(encoded)
            }
        }
    }

    private fun constructEncoderOptions(
        format: ImageFormat,
        quality: Int?,
    ): Array<VipsOption> =
        buildList {
            if (format.vipsProperties.supportsQuality) {
                add(VipsOption.Int(OPTION_QUALITY, quality ?: format.vipsProperties.defaultQuality))
            }
            // Lock AVIF to 4:2:0 chroma subsampling so SVT-AV1 doesn't crash at qualities > 90
            // (which FOREIGN_SUBSAMPLE_AUTO disables subsampling at)
            if (format == ImageFormat.AVIF) {
                add(VipsOption.Enum(OPTION_SUBSAMPLE_MODE, VipsForeignSubsample.FOREIGN_SUBSAMPLE_ON))
                add(VipsOption.Enum(OPTION_ENCODER, VipsForeignHeifEncoder.FOREIGN_HEIF_ENCODER_SVT))
            }
        }.toTypedArray()
}
