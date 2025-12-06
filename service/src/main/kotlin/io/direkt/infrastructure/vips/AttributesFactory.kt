package io.direkt.infrastructure.vips

import app.photofox.vipsffm.VImage
import io.direkt.image.model.Attributes
import io.direkt.image.model.ImageFormat
import io.image.vips.pageSafeHeight

object AttributesFactory {
    fun createAttributes(
        image: VImage,
        sourceFormat: ImageFormat,
        destinationFormat: ImageFormat,
    ): Attributes {
        val height =
            if (sourceFormat.vipsProperties.supportsPaging) {
                image.pageSafeHeight()
            } else {
                image.height
            }

        val supportsPaging = destinationFormat.vipsProperties.supportsPaging

        return Attributes(
            width = image.width,
            height = height,
            format = destinationFormat,
            pageCount = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_N_PAGES) else null,
            loop = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_LOOP) else null,
        )
    }
}