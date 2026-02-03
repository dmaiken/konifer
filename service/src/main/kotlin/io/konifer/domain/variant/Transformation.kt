package io.konifer.domain.variant

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import kotlin.collections.emptyList

data class Transformation(
    val originalVariant: Boolean = false,
    val width: Int,
    val height: Int,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val canUpscale: Boolean = true,
    val format: ImageFormat,
    /**
     * Ignored if [rotate] is [io.konifer.domain.image.Rotate.AUTO]
     */
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
    val padding: Padding = Padding.default,
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
                padding == it.padding
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
        result = 31 * result + fit.hashCode()
        result = 31 * result + gravity.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rotate.hashCode()
        result = 31 * result + filter.hashCode()
        result = 31 * result + padding.hashCode()
        return result
    }
}

data class Padding(
    val amount: Int,
    val color: List<Int>,
) {
    companion object Factory {
        val default =
            Padding(
                amount = 0,
                color = emptyList(),
            )
    }
}
