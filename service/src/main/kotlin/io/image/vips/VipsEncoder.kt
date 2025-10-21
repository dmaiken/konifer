package io.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import io.image.ByteChannelOutputStream
import io.image.model.ImageFormat
import io.image.model.Transformation
import io.image.vips.VipsOption.VIPS_OPTION_QUALITY
import io.ktor.utils.io.ByteChannel

object VipsEncoder {
    private val noOptions = emptyArray<VipsOption>()

    fun writeToStream(
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ) {
        writeToStream(
            source = source,
            format = transformation.format,
            quality = transformation.quality,
            outputChannel = outputChannel,
        )
    }

    fun writeToStream(
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
        ByteChannelOutputStream(outputChannel).use { stream ->
            source.writeToStream(
                stream,
                ".${format.extension}",
                *options,
            )
        }
    }

    private fun constructEncoderOptions(
        format: ImageFormat,
        quality: Int?,
    ): Array<VipsOption> =
        if (format.vipsProperties.supportsQuality) {
            arrayOf(VipsOption.Int(VIPS_OPTION_QUALITY, quality ?: format.vipsProperties.defaultQuality))
        } else {
            noOptions
        }
}
