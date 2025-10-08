package io.asset.repository

import image.model.ImageFormat
import image.model.Transformation
import io.image.model.Fit
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
                ),
            ),
            named(
                "width",
                Transformation(
                    height = 10,
                    width = 101,
                    format = ImageFormat.PNG,
                    fit = Fit.FIT,
                ),
            ),
            named(
                "format",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.JPEG,
                    fit = Fit.FIT,
                ),
            ),
            named(
                "FIT fit",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.PNG,
                    fit = Fit.FILL,
                ),
            ),
            named(
                "STRETCH fit",
                Transformation(
                    height = 10,
                    width = 10,
                    format = ImageFormat.PNG,
                    fit = Fit.STRETCH,
                ),
            ),
        )
}
