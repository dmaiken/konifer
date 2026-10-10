package io.konifer.util

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
import java.io.ByteArrayOutputStream
import java.lang.foreign.ValueLayout

fun pixelsToEncoded(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: String,
    format: ImageFormat = ImageFormat.JPEG,
): ByteArray {
    val uppercaseChannels = channels.uppercase()
    require(uppercaseChannels in setOf("RGB", "BGR", "RGBA", "BGRA", "ARGB", "ABGR"))
    require(pixels.size.toLong() == width.toLong() * height * uppercaseChannels.length)

    val output = ByteArrayOutputStream()
    Vips.run { arena ->
        val raw =
            VImage.newFromMemory(
                arena,
                arena.allocateFrom(ValueLayout.JAVA_BYTE, *pixels),
                width,
                height,
                uppercaseChannels.length,
                VipsBandFormat.FORMAT_UCHAR.rawValue,
            )

        val canonicalChannels = if ('A' in uppercaseChannels) "RGBA" else "RGB"
        val rgb =
            if (uppercaseChannels != canonicalChannels) {
                VImage.bandjoin(
                    arena,
                    canonicalChannels.map { channel -> raw.extractBand(uppercaseChannels.indexOf(channel)) },
                )
            } else {
                raw
            }

        rgb
            .copy(
                VipsOption.Enum(
                    "interpretation",
                    VipsInterpretation.INTERPRETATION_sRGB,
                ),
            ).writeToStream(output, format.extension)
    }
    return output.toByteArray()
}
