package io.konifer.domain.transformation

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Quality(
    val value: Int,
) {
    init {
        require(value in 1..100) { "Quality must be between 1 and 100: $value" }
    }
}

fun Int.toQuality(): Quality = Quality(this)
