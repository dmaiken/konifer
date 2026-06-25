package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsAccess
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.vipsProperties

val noOptions = emptyArray<VipsOption>()

val supportsPagingOptions: Array<VipsOption> = arrayOf(
    // Read all frames
    VipsOption.Int(VipsOptionNames.OPTION_N, -1),
    // Sequential decoding
    VipsOption.Enum(VipsOptionNames.OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
)

val noPagingOptions: Array<VipsOption> = arrayOf(
    // Read only first frame
    VipsOption.Int(VipsOptionNames.OPTION_N, 1),
    // Sequential decoding
    VipsOption.Enum(VipsOptionNames.OPTION_ACCESS, VipsAccess.ACCESS_SEQUENTIAL),
)

fun createDecoderOptions(
    sourceFormat: ImageFormat,
    destinationFormat: ImageFormat,
): Array<VipsOption> {
    if (sourceFormat.vipsProperties.supportsPaging && destinationFormat.vipsProperties.supportsPaging) {
        return supportsPagingOptions
    }
    if (sourceFormat.vipsProperties.supportsPaging) {
        return noPagingOptions
    }
    return noOptions
}
