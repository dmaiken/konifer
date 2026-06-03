package io.konifer.common.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private object ImageFormatParameterValues {
    const val JPEG = "jpg"
    const val PNG = "png"
    const val WEBP = "webp"
    const val AVIF = "avif"
    const val JPEG_XL = "jxl"
    const val HEIC = "heic"
    const val GIF = "gif"
}

@Serializable
enum class ImageFormat(
    val format: String,
    val mimeType: String,
    val extension: String,
    override val queryParameterValue: String = format,
) : Manipulation {
    @SerialName(ImageFormatParameterValues.JPEG)
    JPEG(
        format = ImageFormatParameterValues.JPEG,
        mimeType = "image/jpeg",
        extension = ".jpeg",
    ),

    @SerialName(ImageFormatParameterValues.PNG)
    PNG(
        format = ImageFormatParameterValues.PNG,
        mimeType = "image/png",
        extension = ".png",
    ),

    @SerialName(ImageFormatParameterValues.WEBP)
    WEBP(
        format = ImageFormatParameterValues.WEBP,
        mimeType = "image/webp",
        extension = ".webp",
    ),

    @SerialName(ImageFormatParameterValues.AVIF)
    AVIF(
        format = ImageFormatParameterValues.AVIF,
        mimeType = "image/avif",
        extension = ".avif",
    ),

    @SerialName(ImageFormatParameterValues.JPEG_XL)
    JPEG_XL(
        format = ImageFormatParameterValues.JPEG_XL,
        mimeType = "image/jxl",
        extension = ".jxl",
    ),

    @SerialName(ImageFormatParameterValues.HEIC)
    HEIC(
        format = ImageFormatParameterValues.HEIC,
        mimeType = "image/heic",
        extension = ".heic",
    ),

    @SerialName(ImageFormatParameterValues.GIF)
    GIF(
        format = ImageFormatParameterValues.GIF,
        mimeType = "image/gif",
        extension = ".gif",
    ),
    ;

    companion object Factory
}
