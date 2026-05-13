package io.konifer.infrastructure.datastore

import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import org.junit.jupiter.api.Named.named

object AssetRepositoryTestDataProviders {
    @JvmStatic
    fun variantTransformationSource() =
        listOf(
            named(
                "height",
                Transformation(
                    height = 101,
                    width = 10,
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "width",
                Transformation(
                    height = 10,
                    width = 101,
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "format",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.JPEG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "FIT fit",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.PNG,
                    fit = Fit.FILL,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "STRETCH fit",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.PNG,
                    fit = Fit.STRETCH,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
        )
}
