package io.konifer.domain.variant

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.domain.image.ImageFormat
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.pageSafeHeight
import java.nio.file.Path

data class Attributes(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val orientation: Int = 1,
    val pageCount: Int? = null,
    val loop: Int? = null,
) {
    companion object AttributesFactory {
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
                orientation = image.getInt(VipsOptionNames.OPTION_ORIENTATION) ?: 1,
                pageCount = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_N_PAGES) ?: 1 else null,
                loop = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_LOOP) ?: 0 else null,
            )
        }

        /**
         * Uses Vips to derive image attributes. This is safe to do since vips will not load the entire image into memory
         * and only reads image headers/metadata. It is demand-driven so it will only load what it needs.
         */
        fun createAttributes(
            path: Path,
            format: ImageFormat,
        ): Attributes {
            var attributes: Attributes? = null
            Vips.run { arena ->
                val decoderOptions =
                    createDecoderOptions(
                        sourceFormat = format,
                        destinationFormat = format,
                    )
                val sourceImage = VImage.newFromFile(arena, path.toFile().absolutePath, *decoderOptions)

                attributes =
                    createAttributes(
                        image = sourceImage,
                        sourceFormat = format,
                        destinationFormat = format,
                    )
            }

            return checkNotNull(attributes)
        }
    }
}
