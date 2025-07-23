package image.model

import kotlinx.serialization.Serializable

data class ProcessedImage(
    val attributes: ImageAttributes,
    val lqip: LQIPs,
)

data class RequestedImageAttributes(
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
) {
    companion object Factory {
        val ORIGINAL_VARIANT =
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

@Serializable
data class LQIPs(
    val blurhash: String?,
    val thumbhash: String?,
) {
    companion object Factory {
        val NONE = LQIPs(null, null)
    }
}
