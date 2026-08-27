package io.konifer.domain.variant.preprocessing

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.transformation.RequestedTransformation
import io.konifer.domain.transformation.toBlur
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.transformation.toPaddingAmount
import io.konifer.domain.transformation.toQuality
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.TransformPropertyKeys.PreProcessingPropertyKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImagePreProcessingProperties(
    @SerialName(PreProcessingPropertyKeys.ImagePreProcessingPropertyKeys.MAX_WIDTH)
    val maxWidth: Int? = null,
    @SerialName(PreProcessingPropertyKeys.ImagePreProcessingPropertyKeys.MAX_HEIGHT)
    val maxHeight: Int? = null,
    @SerialName(ManipulationParameters.WIDTH)
    val width: Int? = null,
    @SerialName(ManipulationParameters.HEIGHT)
    val height: Int? = null,
    @SerialName(ManipulationParameters.FORMAT)
    val format: ImageFormat? = null,
    @SerialName(ManipulationParameters.FIT)
    val fit: Fit = Fit.default,
    @SerialName(ManipulationParameters.GRAVITY)
    val gravity: Gravity = Gravity.default,
    @SerialName(ManipulationParameters.ROTATE)
    val rotate: Rotate = Rotate.default,
    @SerialName(ManipulationParameters.FLIP)
    val flip: Flip = Flip.default,
    @SerialName(ManipulationParameters.FILTER)
    val filter: Filter = Filter.default,
    @SerialName(ManipulationParameters.BLUR)
    val blur: Int? = null,
    @SerialName(ManipulationParameters.QUALITY)
    val quality: Int? = null,
    @SerialName(ManipulationParameters.PAD)
    val pad: Int? = null,
    @SerialName(ManipulationParameters.PAD_COLOR)
    val padColor: String? = null,
    @SerialName(ManipulationParameters.STRIP)
    val strip: Set<String> = emptySet(),
    @SerialName(ManipulationParameters.COLOR_SPACE)
    val colorSpace: TransformableColorSpace = TransformableColorSpace.default,
) {
    companion object Factory {
        val default =
            ImagePreProcessingProperties(
                maxWidth = null,
                maxHeight = null,
                width = null,
                height = null,
                format = null,
                fit = Fit.default,
                gravity = Gravity.default,
                rotate = Rotate.default,
                flip = Flip.default,
                filter = Filter.default,
                blur = null,
                quality = null,
                pad = null,
                padColor = null,
                strip = emptySet(),
                colorSpace = TransformableColorSpace.default,
            )
    }

    val requestedImageTransformation = toRequestedImageTransformation()

    private fun toRequestedImageTransformation(): RequestedTransformation =
        RequestedTransformation(
            width = (width ?: maxWidth)?.toDimension(),
            height = (height ?: maxHeight)?.toDimension(),
            format = format,
            fit = fit,
            gravity = gravity,
            rotate = rotate,
            flip = flip,
            canUpscale = maxWidth == null && maxHeight == null,
            filter = filter,
            blur = blur?.toBlur(),
            quality = quality?.toQuality(),
            pad = pad?.toPaddingAmount(),
            padColor = padColor,
            stripMetadata = strip.joinToString(","),
            colorSpace = colorSpace,
        )
}
