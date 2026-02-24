package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import io.konifer.domain.image.ImageFormat
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_QUALITY
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.nio.file.Path
import kotlin.io.path.pathString

object VipsEncoder {
    fun writeToFile(
        source: VImage,
        file: Path,
        format: ImageFormat,
        quality: Int?,
    ) {
        val options =
            constructEncoderOptions(
                format = format,
                quality = quality,
            )
        file.toFile().createNewFile()
        source.writeToFile(file.pathString, *options)
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

        source.writeToStream(outputChannel.toOutputStream(), format.extension, *options)
    }

    private fun constructEncoderOptions(
        format: ImageFormat,
        quality: Int?,
    ): Array<VipsOption> =
        buildList {
            if (format.vipsProperties.supportsQuality) {
                add(VipsOption.Int(OPTION_QUALITY, quality ?: format.vipsProperties.defaultQuality))
            }
        }.toTypedArray()
}
