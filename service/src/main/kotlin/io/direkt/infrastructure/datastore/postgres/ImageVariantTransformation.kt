package io.direkt.infrastructure.datastore.postgres

import io.direkt.domain.image.Filter
import io.direkt.domain.image.Fit
import io.direkt.domain.image.Gravity
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.Rotate
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.Transformation
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [Attributes] because it will be serialized into the datastore.
 */
@Serializable
data class ImageVariantTransformation(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val fit: Fit,
    val gravity: Gravity,
    val rotate: Rotate,
    val horizontalFlip: Boolean,
    val filter: Filter,
    val blur: Int,
    val quality: Int,
    val pad: Int,
    val background: List<Int>,
) {
    companion object Factory {
        fun originalTransformation(attributes: Attributes) =
            ImageVariantTransformation(
                width = attributes.width,
                height = attributes.height,
                format = attributes.format,
                fit = Fit.default,
                gravity = Gravity.default,
                rotate = Rotate.default,
                horizontalFlip = false,
                filter = Filter.default,
                blur = 0,
                quality = attributes.format.vipsProperties.defaultQuality,
                pad = 0,
                background = emptyList(),
            )

        fun from(transformation: Transformation): ImageVariantTransformation =
            ImageVariantTransformation(
                width = transformation.width,
                height = transformation.height,
                format = transformation.format,
                fit = transformation.fit,
                gravity = transformation.gravity,
                rotate = transformation.rotate,
                horizontalFlip = transformation.horizontalFlip,
                filter = transformation.filter,
                blur = transformation.blur,
                quality = transformation.quality,
                pad = transformation.pad,
                background = transformation.background,
            )
    }

    fun toTransformation(): Transformation =
        Transformation(
            width = this.width,
            height = this.height,
            format = this.format,
            fit = this.fit,
            gravity = this.gravity,
            rotate = this.rotate,
            horizontalFlip = this.horizontalFlip,
            filter = this.filter,
            blur = this.blur,
            quality = this.quality,
            pad = this.pad,
            background = this.background,
        )
}
