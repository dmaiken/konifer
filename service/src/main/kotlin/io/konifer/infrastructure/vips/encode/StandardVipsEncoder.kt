package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VImage
import io.konifer.domain.transformation.Transformation
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.lang.foreign.Arena

object StandardVipsEncoder : VipsEncoder {
    override fun writeToStream(
        arena: Arena,
        source: VImage,
        transformation: Transformation,
        outputChannel: ByteChannel,
    ) {
        val options =
            constructEncoderOptions(
                format = transformation.format,
                quality = transformation.quality.value,
            )

        source.writeToStream(outputChannel.toOutputStream(), transformation.format.extension, *options)
    }
}
