package io

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsBandFormat
import app.photofox.vipsffm.enums.VipsInterpretation
import app.photofox.vipsffm.enums.VipsSize
import org.apache.commons.math3.transform.DctNormalization
import org.apache.commons.math3.transform.FastCosineTransformer
import org.apache.commons.math3.transform.TransformType
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.Raster
import java.lang.foreign.ValueLayout

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

object PHash {
    private val dct = FastCosineTransformer(DctNormalization.ORTHOGONAL_DCT_I)

    fun compute(bytes: ByteArray): Long {
        var computed = 0L
        Vips.run { arena ->
            val size = 33 // 2^5 + 1, required by FastCosineTransformer

            // Load & normalize
            val img =
                VImage.newFromBytes(arena, bytes)
                    .thumbnailImage(
                        size,
                        VipsOption.Int("height", size),
                        VipsOption.Enum("size", VipsSize.SIZE_FORCE),
                    )
                    .colourspace(VipsInterpretation.INTERPRETATION_B_W)
                    .cast(VipsBandFormat.FORMAT_FLOAT)

            // Extract pixels into double matrix
            val buffer = img.writeToMemory()
            val matrix =
                Array(size) { r ->
                    DoubleArray(size) { c ->
                        val idx = (r * size + c) * 4L
                        buffer.get(ValueLayout.JAVA_FLOAT, idx).toDouble()
                    }
                }

            // Run 2D DCT
            val dctMatrix = dct2D(matrix)

            // Take top-left 8x8 block (skip DC term at 0,0)
            val block = mutableListOf<Double>()
            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    if (!(r == 0 && c == 0)) {
                        block.add(dctMatrix[r][c])
                    }
                }
            }

            // Compute median
            val median = block.sorted()[block.size / 2]

            // Build 64-bit hash
            for ((i, v) in block.withIndex()) {
                if (v > median) {
                    computed = computed or (1L shl i)
                }
            }
        }

        return computed
    }

    private fun dct2D(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val n = matrix.size

        // Apply DCT on rows
        val rowTransformed =
            Array(n) { r ->
                dct.transform(matrix[r], TransformType.FORWARD)
            }

        // Apply DCT on columns
        val colTransformed = Array(n) { DoubleArray(n) }
        for (c in 0 until n) {
            val col = DoubleArray(n) { r -> rowTransformed[r][c] }
            val dctCol = dct.transform(col, TransformType.FORWARD)
            for (r in 0 until n) {
                colTransformed[r][c] = dctCol[r]
            }
        }

        return colTransformed
    }

    fun hammingDistance(
        h1: Long,
        h2: Long,
    ): Int = java.lang.Long.bitCount(h1 xor h2)

    fun hammingDistance(
        h1: ByteArray,
        h2: ByteArray,
    ): Int = hammingDistance(compute(h1), compute(h2))
}
