package io.konifer.domain.transformation

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.variant.attribute.Attributes
import kotlin.collections.emptyList

data class Transformation(
    val width: Dimension,
    val height: Dimension,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val canUpscale: Boolean = true,
    val format: ImageFormat,
    /**
     * Ignored if [rotate] is [Rotate.AUTO]
     */
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Blur = 0.toBlur(),
    val quality: Quality = format.vipsProperties.defaultQuality.toQuality(),
    val colorSpace: ColorSpace,
    /**
     * Whether the customer explicitly requested this color space or if it was derived from the
     * source image's attributes
     */
    val isColorSpaceLocked: Boolean = false,
    val padding: PaddingTransformation = PaddingTransformation.default,
    val metadata: MetadataTransformation = MetadataTransformation.default,
    val isAutoRotate: Boolean = false,
    val pixels: PixelTransformation? = null,
) {
    init {
        if (format != ImageFormat.PIXELS) {
            require(pixels == null) {
                "Cannot have pixel transformation if format is not pixels"
            }
        }
    }

    companion object {
        /** Describes the transformation identity of stored original content for variant matching. */
        fun fromAttributes(attributes: Attributes): Transformation =
            Transformation(
                width = attributes.width,
                height = attributes.height,
                format = attributes.format,
                colorSpace = attributes.colorSpace,
                pixels = attributes.pixels?.let { PixelTransformation(channels = it.channels) },
            )
    }
}

data class PaddingTransformation(
    val amount: PaddingAmount,
    val color: List<Int>,
) {
    companion object Factory {
        val default =
            PaddingTransformation(
                amount = 0.toPaddingAmount(),
                color = emptyList(),
            )
    }
}

data class MetadataTransformation(
    val strip: Set<MetadataType>,
) {
    companion object Factory {
        val default =
            MetadataTransformation(
                strip = emptySet(),
            )
    }
}
