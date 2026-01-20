package io.konifer.infrastructure.datastore

import io.konifer.domain.image.Fit
import io.konifer.domain.image.ImageFormat
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
