package io.konifer.domain.variant

import io.konifer.domain.image.ImageFormat

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val pageCount: Int? = null,
    val loop: Int? = null,
)
