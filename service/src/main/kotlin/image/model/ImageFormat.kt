package image.model

import io.image.model.VipsProperties

enum class ImageFormat(
    val value: Set<String>,
    val mimeType: String,
    val extension: String,
    val vipsProperties: VipsProperties,
) {
    JPEG(setOf("jpeg", "jpg"), "image/jpeg", "jpeg", VipsProperties(true)),
    PNG(setOf("png"), "image/png", "png", VipsProperties(false)),
    WEBP(setOf("webp"), "image/webp", "webp", VipsProperties(true)),
    AVIF(setOf("avif"), "image/avif", "avif", VipsProperties(true)),
    ;

    companion object {
        fun fromFormat(string: String): ImageFormat {
            return entries.firstOrNull {
                it.value.contains(string.lowercase())
            } ?: throw IllegalArgumentException("Unsupported image format: $string")
        }

        fun fromMimeType(string: String): ImageFormat {
            return entries.firstOrNull {
                it.mimeType.equals(string, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unsupported image mime type: $string")
        }
    }
}
