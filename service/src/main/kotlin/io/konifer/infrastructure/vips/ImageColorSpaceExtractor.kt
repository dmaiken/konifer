package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsInterpretation
import com.drew.lang.ByteArrayReader
import com.drew.metadata.Metadata
import com.drew.metadata.icc.IccDirectory
import com.drew.metadata.icc.IccReader
import io.konifer.domain.image.ColorSpace
import io.ktor.util.moveToByteArray
import kotlin.math.abs

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

            extract(iccDirectory)
        } ?: extractColorspaceInterpretation(image)

    fun extract(iccDirectory: IccDirectory): ColorSpace {
        // Check for Grayscale / CMYK first
        val colorSpaceSignature = iccDirectory.getString(IccDirectory.TAG_COLOR_SPACE)?.trim()
        if (colorSpaceSignature == "GRAY") return ColorSpace.Grayscale
        if (colorSpaceSignature == "CMYK") return ColorSpace.CMYK

        // Extract the mathematical colorants
        val redXyz = readXyzNumber(iccDirectory, IccDirectory.TAG_TAG_rXYZ)
        val greenXyz = readXyzNumber(iccDirectory, IccDirectory.TAG_TAG_gXYZ)

        if (redXyz == null || greenXyz == null) {
            return ColorSpace.Custom("Missing Math Coordinates")
        }

        // Match the fingerprints using a tolerance
        // We only really need to check the 'X' coordinate of Red and Green
        // to uniquely identify the major spaces.
        return when {
            isClose(redXyz[0], 0.515) && isClose(greenXyz[0], 0.292) -> ColorSpace.P3
            isClose(redXyz[0], 0.609) && isClose(greenXyz[0], 0.205) -> ColorSpace.AdobeRGB
            isClose(redXyz[0], 0.436) && isClose(greenXyz[0], 0.385) -> ColorSpace.SRGB

            else -> {
                val rawDescription = iccDirectory.getDescription(IccDirectory.TAG_TAG_desc)
                val cleanName = cleanProfileDescription(rawDescription)
                cleanName?.let { ColorSpace.Custom(it) } ?: ColorSpace.Unknown
            }
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

        return extractedName.trim().take(50).lowercase()
    }

    /**
     * Decodes the ICC s15Fixed16Number format into an array of 3 Doubles (X, Y, Z).
     */
    private fun readXyzNumber(
        directory: IccDirectory,
        tag: Int,
    ): DoubleArray? {
        val bytes = directory.getByteArray(tag) ?: return null

        // An XYZ tag data block is 20 bytes total:
        // 0-3: Type Signature ("XYZ ")
        // 4-7: Reserved (00 00 00 00)
        // 8-11: X coordinate (s15Fixed16) <--- START HERE
        // 12-15: Y coordinate
        // 16-19: Z coordinate
        if (bytes.size < 20) return null

        fun readInt(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF shl 24) or
                (bytes[offset + 1].toInt() and 0xFF shl 16) or
                (bytes[offset + 2].toInt() and 0xFF shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

        // Skip the first 8 bytes of header
        val x = readInt(8) / 65536.0
        val y = readInt(12) / 65536.0
        val z = readInt(16) / 65536.0

        return doubleArrayOf(x, y, z)
    }

    /**
     * Tolerance matching. Different LittleCMS versions might round
     * 0.4360 to 0.4361, so we use a safe delta of 0.01.
     */
    private fun isClose(
        actual: Double,
        target: Double,
        tolerance: Double = 0.01,
    ): Boolean = abs(actual - target) <= tolerance
}
