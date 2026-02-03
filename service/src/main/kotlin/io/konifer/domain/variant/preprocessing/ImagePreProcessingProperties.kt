package io.konifer.domain.variant.preprocessing

import io.konifer.domain.image.Filter
import io.konifer.domain.image.Fit
import io.konifer.domain.image.Flip
import io.konifer.domain.image.Gravity
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.Rotate
import io.konifer.infrastructure.properties.ConfigurationPropertyKeys
import io.konifer.service.context.RequestedTransformation
import io.konifer.service.context.selector.ManipulationParameters
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

data class ImagePreProcessingProperties(
    val maxWidth: Int?,
    val maxHeight: Int?,
    val width: Int?,
    val height: Int?,
    val format: ImageFormat?,
    val fit: Fit,
    val gravity: Gravity,
    val rotate: Rotate,
    val flip: Flip,
    val filter: Filter,
    val blur: Int?,
    val quality: Int?,
    val pad: Int?,
    val background: String?,
) {
    init {
        validate()
    }

    // I think we can be smarter about this
    val enabled: Boolean =
        maxWidth != null ||
            maxHeight != null ||
            format != null ||
            fit != Fit.default ||
            rotate != Rotate.default ||
            flip != Flip.default ||
            filter != Filter.default ||
            (blur != null && blur > 0) ||
            quality != null ||
            (pad != null && pad > 0)

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
                background = null,
            )

        fun create(
            maxWidth: Int?,
            maxHeight: Int?,
            width: Int?,
            height: Int?,
            format: ImageFormat?,
            fit: Fit,
            gravity: Gravity,
            rotate: Rotate,
            flip: Flip,
            filter: Filter,
            blur: Int?,
            quality: Int?,
            pad: Int?,
            background: String?,
        ) = ImagePreProcessingProperties(
            maxWidth,
            maxHeight,
            width,
            height,
            format,
            fit,
            gravity,
            rotate,
            flip,
            filter,
            blur,
            quality,
            pad,
            background,
        )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: ImagePreProcessingProperties?,
        ) = create(
            maxWidth =
                applicationConfig
                    ?.tryGetString(ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_WIDTH)
                    ?.toInt() ?: parent?.maxWidth,
            maxHeight =
                applicationConfig
                    ?.tryGetString(ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_HEIGHT)
                    ?.toInt() ?: parent?.maxHeight,
            width =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.WIDTH)
                    ?.toInt() ?: parent?.width,
            height =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.HEIGHT)
                    ?.toInt() ?: parent?.height,
            format =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.FORMAT)
                    ?.let {
                        ImageFormat.fromFormat(it)
                    } ?: parent?.format,
            fit =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.FIT)
                    ?.let {
                        Fit.fromString(it)
                    } ?: parent?.fit ?: Fit.default,
            gravity =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.GRAVITY)
                    ?.let {
                        Gravity.fromString(it)
                    } ?: parent?.gravity ?: Gravity.default,
            rotate =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.ROTATE)
                    ?.let {
                        Rotate.fromString(it)
                    } ?: parent?.rotate ?: Rotate.default,
            flip =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.FLIP)
                    ?.let {
                        Flip.fromString(it)
                    } ?: parent?.flip ?: Flip.default,
            filter =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.FILTER)
                    ?.let {
                        Filter.fromString(it)
                    } ?: parent?.filter ?: Filter.default,
            blur =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.BLUR)
                    ?.toInt() ?: parent?.blur,
            quality =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.QUALITY)
                    ?.toInt() ?: parent?.quality,
            pad =
                applicationConfig
                    ?.tryGetString(ManipulationParameters.PAD)
                    ?.toInt() ?: parent?.pad,
            background =
                applicationConfig?.tryGetString(ManipulationParameters.PAD_COLOR)
                    ?: parent?.background,
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
            background = background,
        )

    private fun validate() {
        maxWidth?.let {
            require(it > 0) {
                "'${ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_WIDTH}' must be greater than 0"
            }
        }
        maxHeight?.let {
            require(it > 0) {
                "'${ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_HEIGHT}' must be greater than 0"
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
        background?.let {
            require(it.isNotBlank() && it.length > 3 && it.startsWith('#')) { "'${ManipulationParameters.PAD_COLOR}' must not be blank" }
        }
    }
}
