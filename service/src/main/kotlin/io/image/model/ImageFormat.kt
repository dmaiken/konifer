package io.image.model

enum class ImageFormat(
    val format: Set<String>,
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
            supportsAlpha = false,
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
            supportsAlpha = true,
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
            supportsAlpha = true,
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
            supportsAlpha = true,
        ),
    ),
    JPEG_XL(
        setOf("jxl"),
        "image/jxl",
        "jxl",
        VipsProperties(
            supportsQuality = true,
            defaultQuality = 90,
            supportsAlpha = true,
        ),
    ),
    HEIC(
        setOf("heic"),
        "image/heic",
        "heic",
        VipsProperties(
            supportsQuality = true,
            defaultQuality = 50,
            supportsAlpha = true,
        ),
    ),
    GIF(
        setOf("gif"),
        "image/gif",
        "gif",
        VipsProperties(
            supportsQuality = false,
            defaultQuality = 100,
            supportsAlpha = false,
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
