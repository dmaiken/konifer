package io.image

import app.photofox.vipsffm.VImage
import io.image.model.Attributes
import io.image.model.GifAttributes
import io.image.model.ImageFormat
import io.image.vips.VipsOptionNames.OPTION_N_PAGES
import io.image.vips.VipsOptionNames.OPTION_PAGE_HEIGHT

object AttributesFactory {
    fun createAttributes(
        image: VImage,
        destinationFormat: ImageFormat,
    ): Attributes {
        val height =
            if (destinationFormat == ImageFormat.GIF) {
                // Height is page-height not total height
                image.getInt(OPTION_PAGE_HEIGHT) ?: image.height
            } else {
                image.height
            }

        val gifAttributes =
            if (destinationFormat == ImageFormat.GIF) {
                GifAttributes(
                    pages = image.getInt(OPTION_N_PAGES) ?: 1,
                )
            } else {
                null
            }

        return Attributes(
            width = image.width,
            height = height,
            format = destinationFormat,
            gif = gifAttributes,
        )
    }
}
