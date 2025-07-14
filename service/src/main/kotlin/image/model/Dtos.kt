package image.model

import asset.variant.ImageVariantAttributes
import io.ktor.utils.io.ByteChannel

data class ProcessedImage(
    val channel: ByteChannel,
    val attributes: ImageAttributes,
)

data class RequestedImageAttributes(
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
) {
    companion object Factory {
        fun originalVariant(): RequestedImageAttributes =
            RequestedImageAttributes(
                width = null,
                height = null,
                mimeType = null,
            )
    }

    fun isOriginalVariant(): Boolean {
        return width == null && height == null && mimeType == null
    }

    fun matchesImageAttributes(attributes: ImageVariantAttributes): Boolean {
        if (width != null && height != null) {
            return attributes.width == width || attributes.height == height
        }
        if (width != null && attributes.width != width) {
            return false
        }
        if (height != null && attributes.height != height) {
            return false
        }
        if (mimeType != null && attributes.mimeType != mimeType) {
            return false
        }
        return true
    }
}

data class ImageAttributes(
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    fun toRequestedAttributes(): RequestedImageAttributes {
        return RequestedImageAttributes(
            width = width,
            height = height,
            mimeType = mimeType,
        )
    }
}
