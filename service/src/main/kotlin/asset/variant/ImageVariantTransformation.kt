package asset.variant

import image.model.Attributes
import image.model.ImageFormat
import image.model.Transformation
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Gravity
import io.image.model.Rotate
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [image.model.Attributes] because the ordering of these fields matters!
 * The [AssetVariant.transformationKey] is calculated from this class and it must be backwards-compatible
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
}
