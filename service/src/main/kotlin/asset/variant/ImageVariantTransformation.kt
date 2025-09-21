package asset.variant

import image.model.ImageFormat
import image.model.Transformation
import io.image.model.Fit
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
) {
    companion object Factory {
        fun from(transformation: Transformation): ImageVariantTransformation =
            ImageVariantTransformation(
                width = transformation.width,
                height = transformation.height,
                format = transformation.format,
                fit = transformation.fit,
            )
    }
}
