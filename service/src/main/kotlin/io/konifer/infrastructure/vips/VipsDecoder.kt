package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import io.konifer.common.image.ImageFormat
import java.lang.foreign.Arena
import java.nio.file.Path

object VipsDecoder {
    fun decodeSource(
        arena: Arena,
        destinationFormat: ImageFormat,
        sourceFormat: ImageFormat,
        source: Path,
    ): VImage {
        val decoderOptions =
            createDecoderOptions(
                sourceFormat = sourceFormat,
                destinationFormat = destinationFormat,
            )
        return VImage.newFromFile(arena, source.toFile().absolutePath, *decoderOptions)
    }
}
