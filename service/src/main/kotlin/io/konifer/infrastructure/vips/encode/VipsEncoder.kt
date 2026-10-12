package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.ktor.utils.io.ByteChannel
import java.lang.foreign.Arena

interface VipsEncoder {
    fun writeToStream(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    )
}
