package io.direkt.domain.image

import io.direkt.infrastructure.vips.VipsProperties

enum class ImageFormat(
    val format: String,
    val mimeType: String,
    val extension: String,
    val vipsProperties: VipsProperties,
) {
    JPEG(
        format = "jpg",
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
        format = "png",
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
        format = "webp",
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
        format = "avif",
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
        format = "jxl",
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
        format = "heic",
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
        format = "gif",
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

    companion object Factory {
        fun fromFormat(string: String): ImageFormat =
            entries.firstOrNull {
                it.format == string
            } ?: throw IllegalArgumentException("Unsupported image format: $string")

        fun fromMimeType(string: String): ImageFormat =
            entries.firstOrNull {
                it.mimeType.equals(string, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unsupported image mime type: $string")

        fun fromExtension(extension: String): ImageFormat =
            entries.firstOrNull {
                it.extension == extension
            } ?: throw IllegalArgumentException("Unsupported image extension: $extension")
    }
}
