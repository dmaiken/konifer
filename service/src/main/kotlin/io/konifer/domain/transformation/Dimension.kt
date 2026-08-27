package io.konifer.domain.transformation

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Dimension(
    val value: Int,
) : Comparable<Dimension> {
    init {
        require(value > 0) { "Dimension must be a positive number: $value" }
    }

    override fun compareTo(other: Dimension): Int = value.compareTo(other.value)
}

fun Int.toDimension(): Dimension = Dimension(this)
