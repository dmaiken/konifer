package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import io.konifer.common.image.ImageFormat
import io.konifer.infrastructure.vips.createDecoderOptions
import java.lang.foreign.Arena
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object VipsFileDecoder {
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
        return VImage.newFromFile(arena, source.absolutePathString(), *decoderOptions)
    }
}
