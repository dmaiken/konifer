package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
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
        require(sourceFormat == ImageFormat.PIXELS) { "Raw pixel decoder requires PIXELS format" }
        val attributes = requireNotNull(sourceAttributes) { "Raw pixels require stored source attributes" }
        require(attributes.format == ImageFormat.PIXELS) { "Raw pixel attributes must describe PIXELS format" }
        val layout = requireNotNull(attributes.pixels) { "Raw pixels require stored channel layout" }.channels.value
        val width = attributes.width.value
        val height = attributes.height.value
        val expectedBytes = Math.multiplyExact(width.toLong() * height, layout.length.toLong())
        val actualBytes = Files.size(source)
        require(actualBytes == expectedBytes) {
            "Raw pixel size mismatch: expected $expectedBytes bytes for ${width}x$height $layout, found $actualBytes"
        }

        val raw = VImage.rawload(arena, source.absolutePathString(), width, height, layout.length)
        val canonicalLayout = if (layout.length == 4) "RGBA" else "RGB"
        val ordered =
            if (layout == canonicalLayout) {
                raw
            } else {
                VImage.bandjoin(arena, canonicalLayout.map { channel -> raw.extractBand(layout.indexOf(channel)) })
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
}
