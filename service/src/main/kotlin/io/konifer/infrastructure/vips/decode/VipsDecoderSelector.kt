package io.konifer.infrastructure.vips.decode

import io.konifer.common.image.ImageFormat

object VipsDecoderSelector {
    fun getDecoder(format: ImageFormat): VipsDecoder =
        when (format) {
            ImageFormat.PIXELS -> RawPixelDecoder
            else -> VipsFileDecoder
        }
}
