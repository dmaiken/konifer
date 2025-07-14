package image.model

enum class ImageFormat(
    val value: Set<String>,
    val mimeType: String,
    val extension: String,
) {
    JPEG(setOf("jpeg", "jpg"), "image/jpeg", "jpeg"),
    PNG(setOf("png"), "image/png", "png"),
    WEBP(setOf("webp"), "image/webp", "webp"),
    AVIF(setOf("avif"), "image/avif", "avif"),
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
