package io.konifer.domain.transformation.pixel

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PixelLayout private constructor(
    val value: String,
) {
    init {
        require(value.length in (3..4)) {
            "Pixel layout must be between 3 and 4 characters"
        }
        require(value.all { char -> char in ALLOWED_CHANNELS }) {
            "Pixel layout can only contain ${ALLOWED_CHANNELS.joinToString()}"
        }
    }

    companion object {
        private val ALLOWED_CHANNELS = setOf('R', 'G', 'B', 'A')

        val default = "RGB".toPixelLayout()

        operator fun invoke(value: String): PixelLayout = PixelLayout(value.uppercase())
    }
}

fun String.toPixelLayout() = PixelLayout(this)
