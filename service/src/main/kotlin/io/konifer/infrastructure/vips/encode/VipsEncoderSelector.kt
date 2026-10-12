package io.konifer.infrastructure.vips.encode

import io.konifer.common.image.ImageFormat

object VipsEncoderSelector {
    fun getEncoder(format: ImageFormat): VipsEncoder =
        when (format) {
            ImageFormat.PIXELS -> RawPixelEncoder
            ImageFormat.JPEG_XL -> VipsJpegXlEncoder
            else -> StandardVipsEncoder
        }
}
