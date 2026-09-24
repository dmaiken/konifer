package io.konifer.infrastructure.datastore

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import org.junit.jupiter.api.Named.named

data class TransformationLookupCase(
    val stored: Transformation,
    val nonMatching: Transformation,
)

object AssetRepositoryTestDataProviders {
    private val baseTransformation =
        Transformation(
            height = 10.toDimension(),
            width = 10.toDimension(),
            format = ImageFormat.PNG,
            colorSpace = ColorSpace.SRGB,
        )

    @JvmStatic
    fun transformationLookupSource() =
        buildList {
            addCase("height", baseTransformation.copy(height = 11.toDimension()))
            addCase("width", baseTransformation.copy(width = 11.toDimension()))
            ImageFormat.entries.forEach { format ->
                addCase(
                    name = "format=$format",
                    stored = baseTransformation.copy(format = format),
                    nonMatching = baseTransformation.copy(format = ImageFormat.entries.first { it != format }),
                )
            }
            Fit.entries.forEach { fit ->
                addCase(
                    name = "fit=$fit",
                    stored = baseTransformation.copy(fit = fit),
                    nonMatching = baseTransformation.copy(fit = Fit.entries.first { it != fit }),
                )
            }
            Rotate.entries.forEach { rotate ->
                addCase(
                    name = "rotate=$rotate",
                    stored = baseTransformation.copy(rotate = rotate),
                    nonMatching = baseTransformation.copy(rotate = Rotate.entries.first { it != rotate }),
                )
            }
            listOf(true, false).forEach { horizontalFlip ->
                addCase(
                    name = "horizontalFlip=$horizontalFlip",
                    stored = baseTransformation.copy(horizontalFlip = horizontalFlip),
                    nonMatching = baseTransformation.copy(horizontalFlip = !horizontalFlip),
                )
            }
            Filter.entries.forEach { filter ->
                addCase(
                    name = "filter=$filter",
                    stored = baseTransformation.copy(filter = filter),
                    nonMatching = baseTransformation.copy(filter = Filter.entries.first { it != filter }),
                )
            }
            Gravity.entries.forEach { gravity ->
                addCase(
                    name = "gravity=$gravity",
                    stored = baseTransformation.copy(gravity = gravity),
                    nonMatching = baseTransformation.copy(gravity = Gravity.entries.first { it != gravity }),
                )
            }
            addCase(
                name = "quality",
                stored = baseTransformation.copy(quality = 10.toQuality()),
                nonMatching = baseTransformation.copy(quality = 50.toQuality()),
            )
            addCase(
                name = "blur",
                stored = baseTransformation.copy(blur = 10.toBlur()),
                nonMatching = baseTransformation.copy(blur = 50.toBlur()),
            )
            addCase(
                name = "padding amount",
                stored =
                    baseTransformation.copy(
                        padding = PaddingTransformation(amount = 10.toPaddingAmount(), color = emptyList()),
                    ),
                nonMatching =
                    baseTransformation.copy(
                        padding = PaddingTransformation(amount = 50.toPaddingAmount(), color = emptyList()),
                    ),
            )
            addCase(
                name = "padding color",
                stored =
                    baseTransformation.copy(
                        padding = PaddingTransformation(amount = 0.toPaddingAmount(), color = listOf(255, 255, 255, 255)),
                    ),
                nonMatching =
                    baseTransformation.copy(
                        padding = PaddingTransformation(amount = 0.toPaddingAmount(), color = listOf(240, 255, 255, 255)),
                    ),
            )
            addCase(
                name = "metadata",
                stored =
                    baseTransformation.copy(
                        metadata = MetadataTransformation(strip = setOf(MetadataType.EXIF, MetadataType.XMP, MetadataType.IPTC)),
                    ),
                nonMatching =
                    baseTransformation.copy(
                        metadata = MetadataTransformation(strip = setOf(MetadataType.EXIF, MetadataType.XMP)),
                    ),
            )
            addCase(
                name = "color space",
                stored = baseTransformation.copy(colorSpace = ColorSpace.P3),
                nonMatching = baseTransformation.copy(colorSpace = ColorSpace.SRGB),
            )
        }

    private fun MutableList<org.junit.jupiter.api.Named<TransformationLookupCase>>.addCase(
        name: String,
        nonMatching: Transformation,
        stored: Transformation = baseTransformation,
    ) {
        add(named(name, TransformationLookupCase(stored = stored, nonMatching = nonMatching)))
    }
}
