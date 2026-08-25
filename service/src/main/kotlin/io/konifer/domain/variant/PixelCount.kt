package io.konifer.domain.variant

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PixelCount(
    val value: Long,
) {
    init {
        require(value > 0) {
            "Pixel count must be a positive number: $value"
        }
    }
}

fun Long.toPixelCount(): PixelCount = PixelCount(this)
