package io.konifer.util

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VTarget
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import io.konifer.common.image.ImageFormat
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

    var encoded: ByteArray? = null
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

        // Newer libjxl encoders seek in the encoded output, so the target must be seekable.
        val target = VTarget.newToMemory(arena)
        rgb
            .copy(
                VipsOption.Enum(
                    "interpretation",
                    VipsInterpretation.INTERPRETATION_sRGB,
                ),
            ).writeToTarget(target, format.extension)
        encoded = target.blob.bytes
    }
    return checkNotNull(encoded)
}
