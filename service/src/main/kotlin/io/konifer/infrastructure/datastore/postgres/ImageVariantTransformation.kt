package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.Padding
import io.konifer.domain.variant.Transformation
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [Attributes] because it will be serialized into the datastore.
 */
@Serializable
data class ImageVariantTransformation(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
    val padding: ImageVariantPadding = ImageVariantPadding.default,
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
                padding =
                    ImageVariantPadding(
                        amount = 0,
                        color = emptyList(),
                    ),
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
                padding =
                    ImageVariantPadding(
                        amount = transformation.padding.amount,
                        color = transformation.padding.color,
                    ),
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
            padding =
                Padding(
                    amount = this.padding.amount,
                    color = this.padding.color,
                ),
        )
}

@Serializable
data class ImageVariantPadding(
    val amount: Int,
    val color: List<Int>,
) {
    companion object Factory {
        val default =
            ImageVariantPadding(
                amount = 0,
                color = emptyList(),
            )
    }
}
