package asset.variant

import image.model.ImageFormat
import io.image.model.Fit
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [image.model.Attributes] because the ordering of these fields matters!
 * The [AssetVariant.transformationKey] is calculated from this class and it must be backwards-compatible
 */
@Serializable
data class ImageVariantTransformations(
    val height: Int,
    val width: Int,
    val format: ImageFormat,
    val fit: Fit
)
