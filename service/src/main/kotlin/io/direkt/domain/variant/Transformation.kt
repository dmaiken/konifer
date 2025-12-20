package io.direkt.domain.variant

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate

data class Transformation(
    val originalVariant: Boolean = false,
    val width: Int,
    val height: Int,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val canUpscale: Boolean = true,
    val format: ImageFormat,
    /**
     * Ignored if [rotate] is [io.direkt.domain.image.Rotate.AUTO]
     */
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
    val pad: Int = 0,
    /**
     * Background will be a 4-element list representing RGBA
     */
    val background: List<Int> = emptyList(),
) {
    companion object Factory {
        val ORIGINAL_VARIANT =
            Transformation(
                originalVariant = true,
                width = 1,
                height = 1,
                format = ImageFormat.PNG,
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        (other as Transformation).let {
            return width == it.width &&
                height == it.height &&
                fit == it.fit &&
                gravity == it.gravity &&
                format == it.format &&
                rotate == it.rotate &&
                horizontalFlip == it.horizontalFlip &&
                filter == it.filter &&
                blur == it.blur &&
                quality == it.quality &&
                pad == it.pad &&
                background == it.background
        }
    }

    override fun hashCode(): Int {
        var result = originalVariant.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + canUpscale.hashCode()
        result = 31 * result + horizontalFlip.hashCode()
        result = 31 * result + blur
        result = 31 * result + quality
        result = 31 * result + pad
        result = 31 * result + fit.hashCode()
        result = 31 * result + gravity.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rotate.hashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + background.hashCode()
        return result
    }
}
