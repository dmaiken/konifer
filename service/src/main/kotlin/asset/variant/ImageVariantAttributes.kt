package io.asset.variant

import image.model.ImageFormat
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val height: Int,
    val width: Int,
    val format: ImageFormat,
) {
    val aspectRatio: Double
        get() = width.toDouble() / height
}
