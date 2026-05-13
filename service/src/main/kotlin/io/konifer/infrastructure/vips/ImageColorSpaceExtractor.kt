package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsInterpretation
import com.drew.lang.ByteArrayReader
import com.drew.metadata.Metadata
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.icc.IccReader
import io.konifer.domain.image.ColorSpace
import io.ktor.util.moveToByteArray

object ImageColorSpaceExtractor {
    /**
     * For ICC v4 profiles, the tag is in Multi-Localized Unicode format "1 enUs(value)"
     */
    private val mlucRegex = Regex("""^(?:\d+\s+)?[a-zA-Z]{4}\((.*)\)$""")
    private const val ICC_PROFILE_KEY = "icc-profile-data"
    private val iccReader = IccReader()

    fun extract(image: VImage): ColorSpace =
        image.getBlob(ICC_PROFILE_KEY)?.let { iccBytes ->
            val metadata = Metadata()
            iccReader.extract(ByteArrayReader(iccBytes.asArenaScopedByteBuffer().moveToByteArray()), metadata)
            val iccDirectory = metadata.getFirstDirectoryOfType(IccDirectory::class.java)

            extractProfileName(iccDirectory)
        } ?: extractColorspaceInterpretation(image)

    private fun extractProfileName(iccDirectory: IccDirectory): ColorSpace {
        val rawDescription = iccDirectory.getDescription(IccDirectory.TAG_TAG_desc)
        val cleanName = cleanProfileDescription(rawDescription)

        return if (cleanName != null) {
            when {
                "display p3" in cleanName || "apple rgb" in cleanName || "sp3c" in cleanName -> ColorSpace.P3
                "adobe rgb" in cleanName -> ColorSpace.AdobeRGB
                "srgb" in cleanName -> ColorSpace.SRGB
                "cmyk" in cleanName -> ColorSpace.CMYK
                else -> ColorSpace.Custom(cleanName)
            }
        } else {
            // Empty tag
            ColorSpace.Custom("Unknown Embedded Profile")
        }
    }

    private fun extractColorspaceInterpretation(image: VImage): ColorSpace =
        when (image.interpretation()) {
            VipsInterpretation.INTERPRETATION_RGB.rawValue,
            VipsInterpretation.INTERPRETATION_sRGB.rawValue,
            VipsInterpretation.INTERPRETATION_RGB16.rawValue,
            -> ColorSpace.SRGB
            VipsInterpretation.INTERPRETATION_B_W.rawValue,
            VipsInterpretation.INTERPRETATION_GREY16.rawValue,
            -> ColorSpace.Grayscale
            VipsInterpretation.INTERPRETATION_CMYK.rawValue -> ColorSpace.CMYK
            else -> ColorSpace.Unknown
        }

    private fun cleanProfileDescription(rawDescription: String?): String? {
        if (rawDescription.isNullOrBlank()) return null

        val match = mlucRegex.find(rawDescription)

        val extractedName =
            if (match != null) {
                match.groupValues[1]
            } else {
                rawDescription // Fallback if it's an older v2 profile without mluc formatting
            }

        return extractedName.trim().lowercase()
    }
}
