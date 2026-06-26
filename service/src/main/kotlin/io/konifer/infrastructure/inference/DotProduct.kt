package io.konifer.infrastructure.inference

/**
 * Calculates the dot-product of two vectors. Otherwise known as the cosine similarity.
 *
 * This function assumes the vectors are normalized.
 */
infix fun FloatArray.dot(other: FloatArray): Double {
    require(size == other.size) {
        "Cannot compute dot product for vectors of different sizes: $size != ${other.size}"
    }
    var out = 0.0
    for ((i, element) in this.withIndex()) out += element * other[i]
    return out
}
