package io.konifer.infrastructure.vips.processor

import java.nio.FloatBuffer

data class ImageTensor(
    val values: FloatBuffer,
    val shape: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageTensor

        if (values != other.values) return false
        if (!shape.contentEquals(other.shape)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = values.hashCode()
        result = 31 * result + shape.contentHashCode()
        return result
    }
}
