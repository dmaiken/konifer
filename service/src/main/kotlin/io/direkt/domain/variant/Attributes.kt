package io.direkt.domain.variant

import io.direkt.domain.image.ImageFormat

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val pageCount: Int? = null,
    val loop: Int? = null,
)
