package io.konifer.domain.variant.preprocessing

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import io.konifer.domain.context.RequestedTransformation
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
    init {
        validate()
    }

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

    val requestedImageTransformation by lazy { toRequestedImageTransformation() }

    private fun toRequestedImageTransformation(): RequestedTransformation =
        RequestedTransformation(
            width = width ?: maxWidth,
            height = height ?: maxHeight,
            format = format,
            fit = fit,
            gravity = gravity,
            rotate = rotate,
            flip = flip,
            canUpscale = maxWidth == null && maxHeight == null,
            filter = filter,
            blur = blur,
            quality = quality,
            pad = pad,
            padColor = padColor,
            stripMetadata = strip.joinToString(","),
            colorSpace = colorSpace,
        )

    private fun validate() {
        maxWidth?.let {
            require(it > 0) {
                "'${PreProcessingPropertyKeys.ImagePreProcessingPropertyKeys.MAX_WIDTH}' must be greater than 0"
            }
        }
        maxHeight?.let {
            require(it > 0) {
                "'${PreProcessingPropertyKeys.ImagePreProcessingPropertyKeys.MAX_HEIGHT}' must be greater than 0"
            }
        }
        blur?.let {
            require(it in 0..150) { "'${ManipulationParameters.BLUR}' must be between 0 and 150" }
        }
        quality?.let {
            require(it in 1..100) { "'${ManipulationParameters.QUALITY}' must be between 1 and 100" }
        }
        pad?.let {
            require(it > 0) { "'${ManipulationParameters.PAD}' must be greater than 0" }
        }
        padColor?.let {
            require(it.isNotBlank() && it.length > 3 && it.startsWith('#')) { "'${ManipulationParameters.PAD_COLOR}' must not be blank" }
        }
    }
}
