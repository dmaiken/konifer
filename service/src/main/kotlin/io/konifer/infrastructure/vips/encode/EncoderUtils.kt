package io.konifer.infrastructure.vips.encode

import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsForeignHeifEncoder
import app.photofox.vipsffm.enums.VipsForeignSubsample
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.vipsProperties
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_ENCODER
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_QUALITY
import io.konifer.infrastructure.vips.VipsOptionNames.OPTION_SUBSAMPLE_MODE

fun constructEncoderOptions(
    format: ImageFormat,
    quality: Int,
): Array<VipsOption> =
    buildList {
        if (format.vipsProperties.supportsQuality) {
            add(VipsOption.Int(OPTION_QUALITY, quality))
        }
        // Lock AVIF to 4:2:0 chroma subsampling so SVT-AV1 doesn't crash at qualities > 90
        // (which FOREIGN_SUBSAMPLE_AUTO disables subsampling at)
        if (format == ImageFormat.AVIF) {
            add(VipsOption.Enum(OPTION_SUBSAMPLE_MODE, VipsForeignSubsample.FOREIGN_SUBSAMPLE_ON))
            add(VipsOption.Enum(OPTION_ENCODER, VipsForeignHeifEncoder.FOREIGN_HEIF_ENCODER_SVT))
        }
    }.toTypedArray()
