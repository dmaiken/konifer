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

// Is there a better way to match transformations here? I feel this
// is starting to grow and may become spaghetti (if it's not already)
data class Transformation(
    val width: Dimension,
    val height: Dimension,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    /**
     * Not included in equals() or hashcode()
     */
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
     *
     * Not included in equals() or hashcode()
     */
    val isColorSpaceLocked: Boolean = false,
    val padding: PaddingTransformation = PaddingTransformation.default,
    val metadata: MetadataTransformation = MetadataTransformation.default,
    /**
     * Not included in equals() or hashcode()
     */
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Transformation

        if (horizontalFlip != other.horizontalFlip) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fit != other.fit) return false
        if (gravity != other.gravity) return false
        if (format != other.format) return false
        if (rotate != other.rotate) return false
        if (filter != other.filter) return false
        if (blur != other.blur) return false
        if (quality != other.quality) return false
        if (colorSpace != other.colorSpace) return false
        if (padding != other.padding) return false
        if (metadata != other.metadata) return false
        if (pixels != other.pixels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = horizontalFlip.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + fit.hashCode()
        result = 31 * result + gravity.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rotate.hashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + blur.hashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + colorSpace.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + pixels.hashCode()
        return result
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
