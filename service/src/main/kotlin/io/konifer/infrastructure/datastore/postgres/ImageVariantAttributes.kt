package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.Attributes
import kotlinx.serialization.Serializable

@Serializable
data class ImageVariantAttributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    /**
     * Unknown is the default because existing images may not have this persisted
     */
    @Serializable(with = ColorSpaceSerializer::class)
    val colorSpace: ColorSpace = ColorSpace.Unknown,
    val pageCount: Int? = null,
    val loop: Int? = null,
) {
    companion object Factory {
        fun from(attributes: Attributes) =
            ImageVariantAttributes(
                width = attributes.width.value,
                height = attributes.height.value,
                format = attributes.format,
                colorSpace = attributes.colorSpace,
                pageCount = attributes.pageCount,
                loop = attributes.loop,
            )
    }

    fun toAttributes(): Attributes =
        Attributes(
            width = this.width.toDimension(),
            height = this.height.toDimension(),
            format = this.format,
            pageCount = this.pageCount ?: 1,
            loop = this.loop,
            colorSpace = this.colorSpace,
        )
}
