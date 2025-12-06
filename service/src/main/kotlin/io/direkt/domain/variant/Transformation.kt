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
}
