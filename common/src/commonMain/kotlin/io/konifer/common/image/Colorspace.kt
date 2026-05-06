package io.konifer.common.image

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
}
