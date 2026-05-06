package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsHelper
import app.photofox.vipsffm.VipsValidation
import app.photofox.vipsffm.jextract.VipsRaw
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_PAGE_HEIGHT
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout

fun VImage.aspectRatio(): Double = this.width.toDouble() / this.height.toDouble()

fun VImage.pageSafeHeight(): Int = this.getInt(OPTION_PAGE_HEIGHT) ?: this.height

fun VImage.unPremultiplyIfNecessary(isAlphaPremultiplied: Boolean): VImage =
    if (isAlphaPremultiplied) {
        this.unpremultiply()
    } else {
        this
    }

fun VImage.premultiplyIfNecessary(isAlphaPremultiplied: Boolean): Pair<VImage, Boolean> =
    if (!isAlphaPremultiplied && this.hasAlpha()) {
        Pair(this.premultiply(), true)
    } else {
        Pair(this, isAlphaPremultiplied)
    }

/**
 * Safely extracts a C-blob from libvips into a standard Kotlin ByteArray,
 * bypassing the broken VBlob length-pointer bug.
 */
fun VImage.getBlobBytes(
    arena: Arena,
    name: String,
): ByteArray? {
    val type = VipsHelper.image_get_typeof(arena, this.unsafeStructAddress, name)
    if (type == 0L) {
        return null
    }

    val outPointer = arena.allocate(VipsRaw.C_POINTER)
    val outLengthPointer = arena.allocate(VipsRaw.C_LONG)

    val result =
        VipsHelper.image_get_blob(
            arena,
            this.unsafeStructAddress,
            name,
            outPointer,
            outLengthPointer,
        )

    if (!VipsValidation.isValidResult(result)) {
        return null
    }

    val dataAddress = outPointer.get(VipsRaw.C_POINTER, 0L)
    val length = outLengthPointer.get(VipsRaw.C_LONG, 0L)

    // Ensure we actually got a valid pointer and payload size
    if (dataAddress.address() == 0L || length <= 0L) {
        return null
    }

    // Reinterpret the raw native memory to its actual bounds
    return dataAddress.reinterpret(length).toArray(ValueLayout.JAVA_BYTE)
}

fun VImage.interpretation(): Int = VipsHelper.image_get_interpretation(this.unsafeStructAddress)
