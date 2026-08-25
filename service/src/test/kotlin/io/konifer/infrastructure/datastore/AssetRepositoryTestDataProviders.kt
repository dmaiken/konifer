package io.konifer.infrastructure.datastore

import io.konifer.common.image.Fit
import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import org.junit.jupiter.api.Named.named

object AssetRepositoryTestDataProviders {
    @JvmStatic
    fun variantTransformationSource() =
        listOf(
            named(
                "height",
                Transformation(
                    height = 101.toDimension(),
                    width = 10.toDimension(),
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "width",
                Transformation(
                    height = 10.toDimension(),
                    width = 101.toDimension(),
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "format",
                Transformation(
                    height = 10.toDimension(),
                    width = 10.toDimension(),
                    format = ImageFormat.JPEG,
                    fit = Fit.FIT,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "FIT fit",
                Transformation(
                    height = 10.toDimension(),
                    width = 10.toDimension(),
                    format = ImageFormat.PNG,
                    fit = Fit.FILL,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
            named(
                "STRETCH fit",
                Transformation(
                    height = 10.toDimension(),
                    width = 10.toDimension(),
                    format = ImageFormat.PNG,
                    fit = Fit.STRETCH,
                    colorSpace = ColorSpace.SRGB,
                ),
            ),
        )
}
