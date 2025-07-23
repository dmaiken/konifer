package io

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.Raster

/**
 * Converts an RGBA byte array to a BufferedImage.
 *
 * Assumes this ByteArray contains interleaved RGBA pixel data:
 * [R, G, B, A, R, G, B, A, ...]
 */
fun ByteArray.toBufferedImage(
    width: Int,
    height: Int,
): BufferedImage {
    require(this.size == width * height * 4) {
        "Byte array size does not match width * height * 4"
    }

    val bands = 4
    val isAlphaPremultiplied = false

    val colorModel =
        ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            true,
            isAlphaPremultiplied,
            Transparency.TRANSLUCENT,
            DataBuffer.TYPE_BYTE,
        )

    val raster =
        Raster.createInterleavedRaster(
            DataBufferByte(this, this.size),
            width,
            height,
            width * bands,
            bands,
            // R, G, B, A offsets
            intArrayOf(0, 1, 2, 3),
            null,
        )

    return BufferedImage(colorModel, raster, isAlphaPremultiplied, null)
}
