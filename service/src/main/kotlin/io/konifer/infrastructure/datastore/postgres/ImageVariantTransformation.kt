package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.domain.image.vipsProperties
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.MetadataTransformation
import io.konifer.domain.variant.PaddingTransformation
import io.konifer.domain.variant.Transformation
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [Attributes] because it will be serialized into the datastore.
 */
@Serializable
data class ImageVariantTransformation(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val fit: Fit = Fit.default,
    val gravity: Gravity = Gravity.default,
    val rotate: Rotate = Rotate.default,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.default,
    val blur: Int = 0,
    val quality: Int = format.vipsProperties.defaultQuality,
    val padding: ImageVariantPadding = ImageVariantPadding.default,
    val metadata: ImageVariantMetadata = ImageVariantMetadata.default,
) {
    companion object Factory {
        fun originalTransformation(attributes: Attributes) =
            ImageVariantTransformation(
                width = attributes.width,
                height = attributes.height,
                format = attributes.format,
                fit = Fit.default,
                gravity = Gravity.default,
                rotate = Rotate.default,
                horizontalFlip = false,
                filter = Filter.default,
                blur = 0,
                quality = attributes.format.vipsProperties.defaultQuality,
                padding = ImageVariantPadding.default,
                metadata = ImageVariantMetadata.default,
            )

        fun from(transformation: Transformation): ImageVariantTransformation =
            ImageVariantTransformation(
                width = transformation.width,
                height = transformation.height,
                format = transformation.format,
                fit = transformation.fit,
                gravity = transformation.gravity,
                rotate = transformation.rotate,
                horizontalFlip = transformation.horizontalFlip,
                filter = transformation.filter,
                blur = transformation.blur,
                quality = transformation.quality,
                padding = ImageVariantPadding.fromPaddingTransformation(transformation.padding),
                metadata = ImageVariantMetadata.fromMetadataTransformation(transformation.metadata),
            )
    }

    fun toTransformation(): Transformation =
        Transformation(
            width = this.width,
            height = this.height,
            format = this.format,
            fit = this.fit,
            gravity = this.gravity,
            rotate = this.rotate,
            horizontalFlip = this.horizontalFlip,
            filter = this.filter,
            blur = this.blur,
            quality = this.quality,
            padding =
                PaddingTransformation(
                    amount = this.padding.amount,
                    color = this.padding.color,
                ),
        )
}

@Serializable
data class ImageVariantPadding(
    val amount: Int,
    val color: List<Int>,
) {
    companion object Factory {
        val default =
            ImageVariantPadding(
                amount = 0,
                color = emptyList(),
            )

        fun fromPaddingTransformation(transformation: PaddingTransformation): ImageVariantPadding =
            ImageVariantPadding(
                amount = transformation.amount,
                color = transformation.color,
            )
    }
}

@Serializable
data class ImageVariantMetadata(
    val strip: List<MetadataType>,
) {
    companion object Factory {
        val default =
            ImageVariantMetadata(
                strip = emptyList(),
            )

        fun fromMetadataTransformation(transformation: MetadataTransformation): ImageVariantMetadata =
            // IMPORTANT: this must be sorted alphabetically to ensure proper variant querying!!
            ImageVariantMetadata(
                strip = transformation.strip.toList().sortedBy { it.name },
            )
    }
}
