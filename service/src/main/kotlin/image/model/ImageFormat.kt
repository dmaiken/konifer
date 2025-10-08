package image.model

import io.image.model.VipsProperties

enum class ImageFormat(
    val value: Set<String>,
    val mimeType: String,
    val extension: String,
    val vipsProperties: VipsProperties,
) {
    JPEG(
        setOf("jpeg", "jpg"),
        "image/jpeg",
        "jpeg",
        VipsProperties(
            supportsQuality = true,
            // Sharp's default quality setting
            defaultQuality = 80,
        ),
    ),
    PNG(
        setOf("png"),
        "image/png",
        "png",
        VipsProperties(
            supportsQuality = false,
            // Not used since PNG does not support lossy compression
            defaultQuality = 100,
        ),
    ),
    WEBP(
        setOf("webp"),
        "image/webp",
        "webp",
        VipsProperties(
            supportsQuality = true,
            // Sharp's default quality setting
            defaultQuality = 80,
        ),
    ),
    AVIF(
        setOf("avif"),
        "image/avif",
        "avif",
        VipsProperties(
            supportsQuality = true,
            // Sharp's default quality setting
            defaultQuality = 50,
        ),
    ),
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
