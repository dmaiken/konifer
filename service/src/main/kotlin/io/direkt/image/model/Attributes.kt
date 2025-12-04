package io.direkt.image.model

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val pageCount: Int? = null,
    val loop: Int? = null,
)
