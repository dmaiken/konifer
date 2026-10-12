package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelChannels
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BACKGROUND
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.format
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.lang.foreign.Arena

object RawPixelEncoder : VipsEncoder {
    private val OPAQUE_ALPHA_BAND = listOf(255.0)
    private val RGBA = charArrayOf('R', 'G', 'B', 'A')
    private val RGB = charArrayOf('R', 'G', 'B')

    private val WHITE_CHANNEL = listOf(255.0)

    override fun writeToStream(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ) {
        require(source.format() == VipsBandFormat.FORMAT_UCHAR) {
            "Source pixels not in correct datatype, were they casted? Source datatype: ${source.format().name}"
        }
        if (transformation.pixels?.channels == PixelChannels.grayscale) {
            encodeToGrayscaleChannel(
                arena = arena,
                source = source,
                outputChannel = outputChannel,
            )
        } else {
            encodeToColorChannels(
                arena = arena,
                source = source,
                outputChannel = outputChannel,
                transformation = transformation,
            )
        }
    }

    private fun encodeToGrayscaleChannel(
        arena: Arena,
        source: VImage,
        outputChannel: ByteChannel,
    ) {
        val target =
            VTarget.newFromOutputStream(
                arena,
                outputChannel.toOutputStream(),
            )
        val grayscale = source.colourspace(VipsInterpretation.INTERPRETATION_B_W)
        val opaque =
            if (grayscale.hasAlpha()) {
                grayscale.flatten(
                    VipsOption.ArrayDouble(OPTION_BACKGROUND, WHITE_CHANNEL),
                )
            } else {
                grayscale
            }

        opaque
            .rawsaveTarget(target)
    }

    private fun encodeToColorChannels(
        arena: Arena,
        source: VImage,
        outputChannel: ByteChannel,
        transformation: Transformation,
    ) {
        val bands =
            source.getInt(OPTION_BANDS)
                ?: throw IllegalStateException("Unable to determine image band count")
        val pixelChannels =
            checkNotNull(transformation.pixels) {
                "Pixel channels missing despite format of ${transformation.format.format}"
            }.channels.value.toCharArray()

        require(bands in (3..4)) {
            "Expected 3 or 4 RGB bands when normalizing image tensor but found $bands bands"
        }

        val target =
            VTarget.newFromOutputStream(
                arena,
                outputChannel.toOutputStream(),
            )

        // Check if the requested output is the same as the image bytes
        if ((bands == 4 && pixelChannels.contentEquals(RGBA)) || (bands == 3 && pixelChannels.contentEquals(RGB))) {
            source.rawsaveTarget(target)
            return
        }

        val imageWithAlpha =
            if (bands == 3 && pixelChannels.contains('A')) {
                source.bandjoinConst(OPAQUE_ALPHA_BAND)
            } else {
                source
            }
        val red = imageWithAlpha.extractBand(0)
        val green = imageWithAlpha.extractBand(1)
        val blue = imageWithAlpha.extractBand(2)

        val reordered =
            VImage.bandjoin(
                arena,
                buildList {
                    pixelChannels.forEach { channel ->
                        when (channel) {
                            'R' -> add(red)
                            'G' -> add(green)
                            'B' -> add(blue)
                            'A' -> add(imageWithAlpha.extractBand(3))
                            else -> error("Unsupported channel: $channel")
                        }
                    }
                },
            )

        reordered.rawsaveTarget(target)
    }
}
