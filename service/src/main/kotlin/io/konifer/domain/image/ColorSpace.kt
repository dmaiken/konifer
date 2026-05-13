package io.konifer.domain.image

sealed class ColorSpace(
    val name: String,
) {
    object SRGB : ColorSpace(name = "srgb")

    object P3 : ColorSpace("p3")

    object AdobeRGB : ColorSpace("adobe_rgb")

    object CMYK : ColorSpace("cymk")

    object Grayscale : ColorSpace("grayscale")

    object Unknown : ColorSpace("unknown")

    data class Custom(
        val profileName: String,
    ) : ColorSpace(profileName)

    override fun toString() = "ColorSpace(name=$name)"
}

fun String.toColorSpace(): ColorSpace =
    when (this.lowercase()) {
        "srgb" -> ColorSpace.SRGB
        "p3" -> ColorSpace.P3
        "adobe_rgb" -> ColorSpace.AdobeRGB
        "cymk" -> ColorSpace.CMYK
        "grayscale" -> ColorSpace.Grayscale
        "unknown" -> ColorSpace.Unknown
        // If it doesn't match our known enums, wrap it in the Custom class
        else -> ColorSpace.Custom(this.lowercase())
    }
