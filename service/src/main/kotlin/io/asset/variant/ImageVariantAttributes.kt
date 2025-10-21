package io.asset.variant

import io.image.model.Attributes
import io.image.model.ImageFormat
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
) {
    companion object Factory {
        fun from(attributes: Attributes) =
            ImageVariantAttributes(
                width = attributes.width,
                height = attributes.height,
                format = attributes.format,
            )
    }

    val aspectRatio: Double
        get() = width.toDouble() / height

    fun toAttributes(): Attributes =
        Attributes(
            width = this.width,
            height = this.height,
            format = this.format,
        )
}
