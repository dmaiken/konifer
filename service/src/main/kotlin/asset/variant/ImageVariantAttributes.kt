package asset.variant

import kotlinx.serialization.Serializable

/**
 * This class exists separately from [image.model.ImageAttributes] because the ordering of these fields matters!
 * The [AssetVariant.attributeKey] is calculated from this class and it must be backwards-compatible
 */
@Serializable
data class ImageVariantAttributes(
    val height: Int,
    val width: Int,
    val mimeType: String,
)
