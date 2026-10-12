package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_INTERPRETATION
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ORIENTATION
import java.lang.foreign.Arena
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/** Decodes packed, row-major, unsigned 8-bit channels using stored metadata rather than a file header. */
object RawPixelDecoder : VipsDecoder {
    private const val ICC_PROFILE_DATA = "icc-profile-data"

    override fun decodeSource(
        arena: Arena,
        destinationFormat: ImageFormat,
        sourceFormat: ImageFormat,
        source: Path,
        sourceAttributes: Attributes?,
    ): VImage {
        validateSource(source, sourceFormat, sourceAttributes)
        val attributes = checkNotNull(sourceAttributes)
        val pixels = checkNotNull(attributes.pixels)
        val channels = pixels.channels.value
        val width = attributes.width.value
        val height = attributes.height.value

        if (channels == PixelChannels.grayscale.value) {
            return VImage
                .rawload(arena, source.absolutePathString(), width, height, 1)
                .copy(VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_B_W))
                .apply { set(OPTION_ORIENTATION, attributes.orientation) }
        }

        val raw =
            when (pixels.layout) {
                PixelLayout.INTERLEAVED -> {
                    VImage.rawload(arena, source.absolutePathString(), width, height, channels.length)
                }

                PixelLayout.PLANAR -> {
                    // Map the stored planes as one tall single-band image, then join their views.
                    // rawload provides random access without rendering or copying the full payload.
                    val planes = VImage.rawload(arena, source.absolutePathString(), width, Math.multiplyExact(height, channels.length), 1)
                    VImage.bandjoin(arena, channels.indices.map { band -> planes.extractArea(0, band * height, width, height) })
                }
            }
        val canonicalLayout = if (channels.length == 4) "RGBA" else "RGB"
        val ordered =
            if (channels == canonicalLayout) {
                raw
            } else {
                VImage.bandjoin(arena, canonicalLayout.map { channel -> raw.extractBand(channels.indexOf(channel)) })
            }
        val image =
            ordered.copy(
                VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_sRGB),
            )
        image.set(OPTION_ORIENTATION, attributes.orientation)
        when (attributes.colorSpace) {
            ColorSpace.SRGB -> {
                // The RGB interpretation already identifies these samples as sRGB.
            }

            ColorSpace.P3 -> {
                // Obtain the built-in profile without converting the source's already-P3 samples.
                val profile =
                    VImage
                        .black(arena, 1, 1, VipsOption.Int(OPTION_BANDS, 3))
                        .copy(
                            VipsOption.Enum(OPTION_INTERPRETATION, VipsInterpretation.INTERPRETATION_sRGB),
                        ).iccTransform("p3")
                        .getBlob(ICC_PROFILE_DATA)
                image.set(ICC_PROFILE_DATA, checkNotNull(profile))
            }

            else -> {
                throw IllegalArgumentException("Unsupported raw pixel color space: ${attributes.colorSpace.name}")
            }
        }
        return image
    }

    private fun validateSource(
        source: Path,
        sourceFormat: ImageFormat,
        sourceAttributes: Attributes?,
    ) {
        require(sourceFormat == ImageFormat.PIXELS) { "Raw pixel decoder requires PIXELS format" }
        val attributes = requireNotNull(sourceAttributes) { "Raw pixels require stored source attributes" }
        require(attributes.format == ImageFormat.PIXELS) { "Raw pixel attributes must describe PIXELS format" }

        val layout = requireNotNull(sourceAttributes.pixels) { "Raw pixels require stored channel layout" }.channels.value
        val isGrayscale = layout == PixelChannels.grayscale.value
        require(!isGrayscale || attributes.colorSpace == ColorSpace.Grayscale) {
            "Raw pixel channels $layout require grayscale color space, found ${attributes.colorSpace.name}"
        }
        val width = sourceAttributes.width.value
        val height = sourceAttributes.height.value
        val bands = if (isGrayscale) 1 else layout.length
        val expectedBytes = Math.multiplyExact(width.toLong() * height, bands.toLong())
        val actualBytes = Files.size(source)

        require(actualBytes == expectedBytes) {
            "Raw pixel size mismatch: expected $expectedBytes bytes for ${width}x$height $layout, found $actualBytes"
        }
    }
}
