package io.konifer.domain.image

object ColorSpaceNames {
    const val SRGB = "srgb"
    const val P3 = "p3"
    const val ADOBE_RGB = "adobe_rgb"
    const val CYMK = "cymk"
    const val GRAYSCALE = "grayscale"
    const val UNKNOWN = "unknown"
}

sealed class ColorSpace(
    val name: String,
) {
    object SRGB : ColorSpace(ColorSpaceNames.SRGB)

    object P3 : ColorSpace(ColorSpaceNames.P3)

    object AdobeRGB : ColorSpace(ColorSpaceNames.ADOBE_RGB)

    object CMYK : ColorSpace(ColorSpaceNames.CYMK)

    object Grayscale : ColorSpace(ColorSpaceNames.GRAYSCALE)

    object Unknown : ColorSpace(ColorSpaceNames.UNKNOWN)

    data class Custom(
        val profileName: String,
    ) : ColorSpace(profileName)

    override fun toString() = "ColorSpace(name=$name)"
}

fun String.toColorSpace(): ColorSpace =
    when (this.lowercase()) {
        ColorSpaceNames.SRGB -> ColorSpace.SRGB

        ColorSpaceNames.P3 -> ColorSpace.P3

        ColorSpaceNames.ADOBE_RGB -> ColorSpace.AdobeRGB

        ColorSpaceNames.CYMK -> ColorSpace.CMYK

        ColorSpaceNames.GRAYSCALE -> ColorSpace.Grayscale

        ColorSpaceNames.UNKNOWN -> ColorSpace.Unknown

        // If it doesn't match our known enums, wrap it in the Custom class
        else -> ColorSpace.Custom(this.lowercase())
    }
