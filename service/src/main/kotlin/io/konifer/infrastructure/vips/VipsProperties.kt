package io.konifer.infrastructure.vips

data class VipsProperties(
    val supportsQuality: Boolean,
    val defaultQuality: Int,
    val supportsAlpha: Boolean,
    val supportsPaging: Boolean,
)
