package io.direkt.image.model

enum class ImageFormat(
    val format: Set<String>,
    val mimeType: String,
    val extension: String,
    val vipsProperties: VipsProperties,
) {
    JPEG(
        format = setOf("jpeg", "jpg"),
        mimeType = "image/jpeg",
        extension = ".jpeg",
        vipsProperties =
            VipsProperties(
                supportsQuality = true,
                // Sharp's default quality setting
                defaultQuality = 80,
                supportsAlpha = false,
                supportsPaging = false,
            ),
    ),
    PNG(
        format = setOf("png"),
        mimeType = "image/png",
        extension = ".png",
        vipsProperties =
            VipsProperties(
                supportsQuality = false,
                // Not used since PNG does not support lossy compression
                defaultQuality = 100,
                supportsAlpha = true,
                supportsPaging = false,
            ),
    ),
    WEBP(
        format = setOf("webp"),
        mimeType = "image/webp",
        extension = ".webp",
        vipsProperties =
            VipsProperties(
                supportsQuality = true,
                // Sharp's default quality setting
                defaultQuality = 80,
                supportsAlpha = true,
                supportsPaging = true,
            ),
    ),
    AVIF(
        format = setOf("avif"),
        mimeType = "image/avif",
        extension = ".avif",
        vipsProperties =
            VipsProperties(
                supportsQuality = true,
                // Sharp's default quality setting
                defaultQuality = 50,
                supportsAlpha = true,
                supportsPaging = false,
            ),
    ),
    JPEG_XL(
        format = setOf("jxl"),
        mimeType = "image/jxl",
        extension = ".jxl",
        vipsProperties =
            VipsProperties(
                supportsQuality = true,
                defaultQuality = 90,
                supportsAlpha = true,
                supportsPaging = false,
            ),
    ),
    HEIC(
        format = setOf("heic"),
        mimeType = "image/heic",
        extension = ".heic",
        vipsProperties =
            VipsProperties(
                supportsQuality = true,
                defaultQuality = 50,
                supportsAlpha = true,
                supportsPaging = false,
            ),
    ),
    GIF(
        format = setOf("gif"),
        mimeType = "image/gif",
        extension = ".gif",
        vipsProperties =
            VipsProperties(
                supportsQuality = false,
                defaultQuality = 100,
                supportsAlpha = false,
                supportsPaging = true,
            ),
    ),
    ;

    companion object {
        fun fromFormat(string: String): ImageFormat =
            entries.firstOrNull {
                it.format.contains(string.lowercase())
            } ?: throw IllegalArgumentException("Unsupported image format: $string")

        fun fromMimeType(string: String): ImageFormat =
            entries.firstOrNull {
                it.mimeType.equals(string, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unsupported image mime type: $string")
    }
}
