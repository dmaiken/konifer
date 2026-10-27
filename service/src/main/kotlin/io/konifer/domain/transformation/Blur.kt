package io.konifer.domain.transformation

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Blur(
    val value: Int,
) {
    init {
        if (value !in 0..150) {
            throw InvalidTransformationException("Blur must be between 0 and 150: $value")
        }
    }
}

fun Int.toBlur(): Blur = Blur(this)
