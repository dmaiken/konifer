package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import app.photofox.vipsffm.enums.VipsBandFormat
import io.konifer.domain.transformation.Transformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.format
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.lang.foreign.Arena

object RawPixelEncoder : VipsEncoder {
    private val OPAQUE_ALPHA_BAND = listOf(255.0)
    private val RGBA = charArrayOf('R', 'G', 'B', 'A')
    private val RGB = charArrayOf('R', 'G', 'B')

    override fun writeToStream(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ) {
        val bands =
            source.getInt(OPTION_BANDS)
                ?: throw IllegalStateException("Unable to determine image band count")
        val pixelLayout =
            checkNotNull(transformation.pixels) {
                "Pixel layout missing despite format of ${transformation.format.format}"
            }.channels.value.toCharArray()

        require(bands in (3..4)) {
            "Expected 3 or 4 RGB bands when normalizing image tensor but found $bands bands"
        }

        val image =
            if (source.format() == VipsBandFormat.FORMAT_UCHAR) {
                source
            } else {
                source.cast(VipsBandFormat.FORMAT_UCHAR)
            }

        val target =
            VTarget.newFromOutputStream(
                arena,
                outputChannel.toOutputStream(),
            )

        // Check if the requested output is the same as the image bytes
        if ((bands == 4 && pixelLayout.contentEquals(RGBA)) || (bands == 3 && pixelLayout.contentEquals(RGB))) {
            image.rawsaveTarget(target)
            return
        }

        val imageWithAlpha =
            if (bands == 3 && pixelLayout.contains('A')) {
                image.bandjoinConst(OPAQUE_ALPHA_BAND)
            } else {
                image
            }
        val red = imageWithAlpha.extractBand(0)
        val green = imageWithAlpha.extractBand(1)
        val blue = imageWithAlpha.extractBand(2)

        val reordered =
            VImage.bandjoin(
                arena,
                buildList {
                    pixelLayout.forEach { channel ->
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
