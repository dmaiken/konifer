package image.model

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
}

data class ImageAttributes(
    val width: Int,
    val height: Int,
    val mimeType: String,
)
