package io.konifer.domain.transformation.pixel

import io.konifer.common.image.pixel.PixelLayout

data class PixelTransformation(
    val channels: PixelChannels = PixelChannels.default,
    val layout: PixelLayout = PixelLayout.default,
)
