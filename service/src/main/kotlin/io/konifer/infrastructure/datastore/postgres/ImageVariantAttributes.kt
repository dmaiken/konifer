package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Attributes
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    @Serializable(with = ColorSpaceSerializer::class)
    val colorSpace: ColorSpace,
    val pageCount: Int? = null,
    val loop: Int? = null,
) {
    companion object Factory {
        fun from(attributes: Attributes) =
            ImageVariantAttributes(
                width = attributes.width,
                height = attributes.height,
                format = attributes.format,
                colorSpace = attributes.colorSpace,
                pageCount = attributes.pageCount,
                loop = attributes.loop,
            )
    }

    fun toAttributes(): Attributes =
        Attributes(
            width = this.width,
            height = this.height,
            format = this.format,
            pageCount = this.pageCount,
            loop = this.loop,
            colorSpace = this.colorSpace,
        )
}
