package io.konifer.infrastructure.vips.processor

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.enums.VipsBandFormat
import io.konifer.common.image.ImageFormat
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.variant.TensorTransformation
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_BANDS
import io.konifer.infrastructure.vips.format
import io.konifer.infrastructure.vips.pipeline.VipsPipelines.tensorProcessingPipeline
import java.lang.foreign.Arena

class VipsTensorProcessor {
    init {
        // Not necessary since this will be a long-running service
        Vips.disableOperationCache()
    }

    fun process(
        arena: Arena,
        source: VImage,
        transformation: TensorTransformation,
    ): ImageTensor {
        // Note: You cannot use coroutines in here unless we change up the way the arena is defined
        // FFM requires that only one thread access the native memory arena
        val result = tensorProcessingPipeline.run(arena, source, transformation.toTransformation())

        return ImageTensor(
            values = normalizePixels(result.processed),
            shape = transformation.tensorLayout,
        )
    }

    private fun normalizePixels(output: VImage): FloatArray {
        val width = output.width
        val height = output.height
        val bands =
            output.getInt(OPTION_BANDS)
                ?: throw IllegalStateException("Unable to determine image band count")

        require(bands == RGB_BANDS) {
            "Expected $RGB_BANDS RGB bands when normalizing image tensor but found $bands"
        }

        val image =
            if (output.format() == VipsBandFormat.FORMAT_UCHAR) {
                output
            } else {
                output.cast(VipsBandFormat.FORMAT_UCHAR)
            }

        val pixels = image.writeToMemory().asByteBuffer()
        val planeSize = width * height
        val tensor = FloatArray(RGB_BANDS * planeSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelOffset = y * width + x
                val memoryOffset = pixelOffset * RGB_BANDS

                val red = pixels.get(memoryOffset).toInt() and BYTE_MASK
                val green = pixels.get(memoryOffset + 1).toInt() and BYTE_MASK
                val blue = pixels.get(memoryOffset + 2).toInt() and BYTE_MASK

                tensor[pixelOffset] = normalizeChannel(red)
                tensor[planeSize + pixelOffset] = normalizeChannel(green)
                tensor[(2 * planeSize) + pixelOffset] = normalizeChannel(blue)
            }
        }

        return tensor
    }

    private fun normalizeChannel(value: Int): Float = (value / SIGLIP2_SCALE) - 1.0f

    private fun TensorTransformation.toTransformation(): Transformation =
        Transformation(
            height = height,
            width = width,
            fit = fit,
            gravity = gravity,
            colorSpace = colorSpace,
            format = ImageFormat.PNG, // Ignored for tensor processing
        )

    private companion object {
        const val RGB_BANDS = 3
        const val BYTE_MASK = 0xff
        const val SIGLIP2_SCALE = 127.5f
    }
}
