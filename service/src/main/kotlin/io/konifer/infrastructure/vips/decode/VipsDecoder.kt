package io.konifer.infrastructure.vips.decode

import app.photofox.vipsffm.VImage
import io.konifer.common.image.ImageFormat
import io.konifer.domain.variant.attribute.Attributes
import java.lang.foreign.Arena
import java.nio.file.Path

interface VipsDecoder {
    fun decodeSource(
        arena: Arena,
        destinationFormat: ImageFormat,
        sourceFormat: ImageFormat,
        source: Path,
        sourceAttributes: Attributes? = null,
    ): VImage
}
