package io.direkt.asset.variant

import io.direkt.image.model.Attributes
import io.direkt.image.model.ImageFormat
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val pageCount: Int? = null,
    val loop: Int? = null,
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
            pageCount = this.pageCount,
            loop = this.loop,
        )
}
