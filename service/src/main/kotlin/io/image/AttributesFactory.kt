package io.image

import app.photofox.vipsffm.VImage
import io.image.model.Attributes
import io.image.model.ImageFormat
import io.image.vips.VipsOptionNames.OPTION_LOOP
import io.image.vips.VipsOptionNames.OPTION_N_PAGES
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
            pageCount = if (supportsPaging) image.getInt(OPTION_N_PAGES) else null,
            loop = if (supportsPaging) image.getInt(OPTION_LOOP) else null,
        )
    }
}
