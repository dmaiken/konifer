package io.konifer.common.image

enum class ImageFormat(
    val format: String,
    val mimeType: String,
    val extension: String,
) {
    JPEG(
        format = "jpg",
        mimeType = "image/jpeg",
        extension = ".jpeg",
    ),
    PNG(
        format = "png",
        mimeType = "image/png",
        extension = ".png",
    ),
    WEBP(
        format = "webp",
        mimeType = "image/webp",
        extension = ".webp",
    ),
    AVIF(
        format = "avif",
        mimeType = "image/avif",
        extension = ".avif",
    ),
    JPEG_XL(
        format = "jxl",
        mimeType = "image/jxl",
        extension = ".jxl",
    ),
    HEIC(
        format = "heic",
        mimeType = "image/heic",
        extension = ".heic",
    ),
    GIF(
        format = "gif",
        mimeType = "image/gif",
        extension = ".gif",
    ),
    ;

    companion object Factory
}
