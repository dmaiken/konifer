package io.image.model

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val gif: GifAttributes? = null,
)

data class GifAttributes(
    val pages: Int,
)
