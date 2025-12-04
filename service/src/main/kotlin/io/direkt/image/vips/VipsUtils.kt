package io.direkt.image.vips

import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.direkt.image.model.ImageFormat
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.jvm.nio.toByteReadChannel
import kotlinx.coroutines.runBlocking
import java.nio.channels.FileChannel

val NO_OPTIONS = emptyArray<VipsOption>()

fun createDecoderOptions(
    sourceFormat: ImageFormat,
    destinationFormat: ImageFormat,
): Array<VipsOption> {
    if (sourceFormat.vipsProperties.supportsPaging && destinationFormat.vipsProperties.supportsPaging) {
        return arrayOf(
            // Read all frames
            VipsOption.Int(VipsOptionNames.OPTION_N, -1),
            // Sequential decoding
            VipsOption.Enum(VipsOptionNames.OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
        )
    }
    if (sourceFormat.vipsProperties.supportsPaging) {
        return arrayOf(
            // Read only first frame
            VipsOption.Int(VipsOptionNames.OPTION_N, 1),
            // Sequential decoding
            VipsOption.Enum(VipsOptionNames.OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
        )
    }
    return NO_OPTIONS
}

/**
 * This blocks!!
 */
fun copyFileToByteChannel(
    fileChannel: FileChannel,
    byteChannel: ByteChannel,
): Unit =
    runBlocking {
        fileChannel.toByteReadChannel().copyTo(byteChannel)
    }
