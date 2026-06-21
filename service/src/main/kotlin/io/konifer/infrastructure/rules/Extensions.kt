package io.konifer.infrastructure.rules

import kotlin.math.sqrt

fun FloatArray.l2Normalize(): FloatArray {
    var sum = 0.0
    for (value in this) {
        sum += value * value
    }

    val norm = sqrt(sum).toFloat()
    require(norm > 0f) { "Cannot normalize zero-length embedding" }

    return FloatArray(size) { index -> this[index] / norm }
}
