package io.konifer.domain.transformation

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PaddingAmount(
    val value: Int,
) {
    init {
        if (value < 0) {
            throw InvalidTransformationException("Padding amount must be a non-negative number: $value")
        }
    }
}

fun Int.toPaddingAmount(): PaddingAmount = PaddingAmount(this)
