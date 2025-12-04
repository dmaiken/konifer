package io.direkt.image.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import io.direkt.asset.TemporaryFileFactory.createPreProcessedTempFile
import io.direkt.image.model.ImageFormat
import io.direkt.image.vips.VipsOptionNames.OPTION_QUALITY
import java.io.File

object VipsEncoder {
    fun writeToFile(
        source: VImage,
        format: ImageFormat,
        quality: Int?,
    ): File {
        val processed = createPreProcessedTempFile(extension = format.extension)
        val options =
            constructEncoderOptions(
                format = format,
                quality = quality,
            )
        source.writeToFile(processed.absolutePath, *options)

        return processed
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
