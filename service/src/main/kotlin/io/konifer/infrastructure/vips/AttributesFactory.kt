package io.konifer.infrastructure.vips

import app.photofox.vipsffm.VImage
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.variant.Attributes

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
            pageCount = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_N_PAGES) ?: 1 else null,
            loop = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_LOOP) ?: 0 else null,
        )
    }
}
