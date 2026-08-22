package io.konifer.domain.transformation

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Dimension(
    val value: Int,
) {
    init {
        require(value > 0) { "Dimension must be a positive number: $value" }
    }
}

fun Int.toDimension(): Dimension = Dimension(this)
