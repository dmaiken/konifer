package io.konifer.domain.variant.attribute

import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.transformation.pixel.PixelChannels

data class PixelAttributes(
    val channels: PixelChannels,
    val layout: PixelLayout,
    val dataType: PixelDataType,
)
