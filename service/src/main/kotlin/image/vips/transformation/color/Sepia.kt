package io.image.vips.transformation.color

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.enums.VipsInterpretation
import io.image.vips.transformation.VipsTransformer
import java.lang.foreign.Arena
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Sepia : VipsTransformer {
    companion object {
        var sepiaMatrix =
            doubleArrayOf(
                0.393, 0.769, 0.189,
                0.349, 0.686, 0.168,
                0.272, 0.534, 0.131,
            )
    }

    override fun transform(
        arena: Arena,
        source: VImage,
    ): VImage {
        val buffer = ByteBuffer.allocate(sepiaMatrix.size * 8)
        buffer.order(ByteOrder.nativeOrder())
        sepiaMatrix.forEach { buffer.putDouble(it) }
        val byteArray = buffer.array()
        val matrixImage = VImage.newFromBytes(arena, byteArray)

        return source
            .colourspace(VipsInterpretation.INTERPRETATION_scRGB)
            .recomb(matrixImage)
            .colourspace(VipsInterpretation.INTERPRETATION_sRGB)
    }
}
