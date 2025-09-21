package io.asset.variant

import image.model.ImageFormat
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
) {
    val aspectRatio: Double
        get() = width.toDouble() / height
}
