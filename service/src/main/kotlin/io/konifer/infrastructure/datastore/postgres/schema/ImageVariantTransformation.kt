package io.konifer.infrastructure.datastore.postgres.schema

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.MetadataTransformation
import io.konifer.domain.transformation.PaddingTransformation
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.pixel.PixelTransformation
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.datastore.postgres.ColorSpaceSerializer
import kotlinx.serialization.Serializable

/**
 * This class exists separately from [Attributes] because it will be serialized into the datastore.
 *
 * EVERY new field need defaults, and existing defaults must retain their historical meaning unless the stored data is migrated.
 */
@Serializable
data class ImageVariantTransformation(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val fit: Fit = Fit.FIT,
    val gravity: Gravity = Gravity.CENTER,
    val rotate: Rotate = Rotate.ZERO,
    val horizontalFlip: Boolean = false,
    val filter: Filter = Filter.NONE,
    val blur: Int = 0,
    val quality: Int = format.persistedDefaultQuality(),
    val padding: ImageVariantPadding = ImageVariantPadding(),
    val metadata: ImageVariantMetadata = ImageVariantMetadata(),
    @Serializable(with = ColorSpaceSerializer::class)
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val pixels: ImageVariantPixels? = null,
) {
    companion object Factory {
        fun originalTransformation(attributes: Attributes) = from(Transformation.fromAttributes(attributes))

        fun from(transformation: Transformation): ImageVariantTransformation =
            ImageVariantTransformation(
                width = transformation.width.value,
                height = transformation.height.value,
                format = transformation.format,
                fit = transformation.fit,
                gravity = transformation.gravity,
                rotate = transformation.rotate,
                horizontalFlip = transformation.horizontalFlip,
                filter = transformation.filter,
                blur = transformation.blur.value,
                quality = transformation.quality.value,
                padding = ImageVariantPadding.fromPaddingTransformation(transformation.padding),
                metadata = ImageVariantMetadata.fromMetadataTransformation(transformation.metadata),
                colorSpace = transformation.colorSpace,
                pixels = transformation.pixels?.let(ImageVariantPixels::fromPixelTransformation),
            )
    }

    fun toTransformation(): Transformation =
        Transformation(
            width = this.width.toDimension(),
            height = this.height.toDimension(),
            format = this.format,
            fit = this.fit,
            gravity = this.gravity,
            rotate = this.rotate,
            horizontalFlip = this.horizontalFlip,
            filter = this.filter,
            blur = this.blur.toBlur(),
            quality = this.quality.toQuality(),
            padding =
                PaddingTransformation(
                    amount = this.padding.amount.toPaddingAmount(),
                    color = this.padding.color,
                ),
            metadata =
                MetadataTransformation(
                    strip = this.metadata.strip.toSet(),
                ),
            colorSpace = this.colorSpace,
            pixels =
                this.pixels?.let {
                    PixelTransformation(
                        channels = it.channels.toPixelChannels(),
                        layout = it.layout,
                    )
                },
        )
}

@Serializable
data class ImageVariantPadding(
    val amount: Int = 0,
    val color: List<Int> = emptyList(),
) {
    companion object Factory {
        fun fromPaddingTransformation(transformation: PaddingTransformation): ImageVariantPadding =
            ImageVariantPadding(
                amount = transformation.amount.value,
                color = transformation.color,
            )
    }
}

@Serializable
data class ImageVariantMetadata(
    val strip: List<MetadataType> = emptyList(),
) {
    companion object Factory {
        fun fromMetadataTransformation(transformation: MetadataTransformation): ImageVariantMetadata =
            // IMPORTANT: this must be sorted alphabetically to ensure proper variant querying!!
            ImageVariantMetadata(
                strip = transformation.strip.toList().sortedBy { it.name },
            )
    }
}

@Serializable
data class ImageVariantPixels(
    val channels: String,
    val layout: PixelLayout = PixelLayout.INTERLEAVED,
) {
    companion object Factory {
        fun fromPixelTransformation(pixelTransformation: PixelTransformation): ImageVariantPixels =
            ImageVariantPixels(
                channels = pixelTransformation.channels.value,
                layout = pixelTransformation.layout,
            )
    }
}

private fun ImageFormat.persistedDefaultQuality(): Int =
    when (this) {
        ImageFormat.JPEG, ImageFormat.WEBP -> 80
        ImageFormat.AVIF, ImageFormat.HEIC -> 50
        ImageFormat.JPEG_XL -> 90
        ImageFormat.PNG, ImageFormat.GIF, ImageFormat.PIXELS -> 100
    }
