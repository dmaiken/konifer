package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import io.direkt.domain.image.ImageFormat
import io.direkt.infrastructure.TemporaryFileFactory.createPreProcessedTempFile
import io.direkt.infrastructure.vips.VipsOptionNames.OPTION_QUALITY
import java.io.File

object VipsEncoder {
    fun writeToFile(
        source: VImage,
        file: File,
        format: ImageFormat,
        quality: Int?,
    ) {
        val options =
            constructEncoderOptions(
                format = format,
                quality = quality,
            )
        file.createNewFile()
        source.writeToFile(file.absolutePath, *options)
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
