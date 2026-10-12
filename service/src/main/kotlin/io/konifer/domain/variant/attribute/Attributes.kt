package io.konifer.domain.variant.attribute

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.transformation.Dimension
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.ImageColorSpaceExtractor
import io.konifer.infrastructure.vips.VipsOptionNames
import io.konifer.infrastructure.vips.createDecoderOptions
import io.konifer.infrastructure.vips.pageSafeHeight
import java.nio.file.Path

data class Attributes(
    val width: Dimension,
    val height: Dimension,
    val format: ImageFormat,
    val orientation: Int = 1,
    val colorSpace: ColorSpace,
    val pageCount: Int = 1,
    val loop: Int? = null,
    val pixels: PixelAttributes? = null,
) {
    companion object Factory {
        fun createAttributes(
            image: VImage,
            sourceFormat: ImageFormat,
            destinationFormat: ImageFormat,
            pixelTransformation: PixelTransformation?,
        ): Attributes {
            val height =
                if (sourceFormat.vipsProperties.supportsPaging) {
                    image.pageSafeHeight()
                } else {
                    image.height
                }

            val supportsPaging = destinationFormat.vipsProperties.supportsPaging

            return Attributes(
                width = image.width.toDimension(),
                height = height.toDimension(),
                format = destinationFormat,
                colorSpace = ImageColorSpaceExtractor.extract(image),
                orientation = image.getInt(VipsOptionNames.OPTION_ORIENTATION) ?: 1,
                pageCount = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_N_PAGES) ?: 1 else 1,
                loop = if (supportsPaging) image.getInt(VipsOptionNames.OPTION_LOOP) ?: 0 else null,
                pixels =
                    if (destinationFormat == ImageFormat.PIXELS) {
                        pixelTransformation?.let {
                            PixelAttributes(
                                channels = it.channels,
                            )
                        }
                    } else {
                        null
                    },
            )
        }

        /**
         * Uses Vips to derive image attributes. This is safe to do since vips will not load the entire image into memory
         * and only reads image headers/metadata. It is demand-driven so it will only load what it needs.
         */
        fun createSourceAttributes(
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
                        // Pixel source format is not allowed, so there are no pixel attributes
                        pixelTransformation = null,
                    )
            }

            return checkNotNull(attributes)
        }
    }
}
