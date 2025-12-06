package io.direkt.domain.image

import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.LQIP
import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PREPROCESSING
import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.IMAGE_FORMAT
import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_HEIGHT
import io.direkt.infrastructure.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_WIDTH
import io.direkt.infrastructure.properties.ValidatedProperties
import io.direkt.infrastructure.properties.validateAndCreate
import io.direkt.infrastructure.tryGetConfig
import io.direkt.service.context.ManipulationParameters.BACKGROUND
import io.direkt.service.context.ManipulationParameters.BLUR
import io.direkt.service.context.ManipulationParameters.FILTER
import io.direkt.service.context.ManipulationParameters.FIT
import io.direkt.service.context.ManipulationParameters.FLIP
import io.direkt.service.context.ManipulationParameters.GRAVITY
import io.direkt.service.context.ManipulationParameters.HEIGHT
import io.direkt.service.context.ManipulationParameters.PAD
import io.direkt.service.context.ManipulationParameters.QUALITY
import io.direkt.service.context.ManipulationParameters.ROTATE
import io.direkt.service.context.ManipulationParameters.WIDTH
import io.direkt.service.context.RequestedTransformation
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList

class ImageProperties private constructor(
    val preProcessing: PreProcessingProperties,
    val previews: Set<LQIPImplementation>,
) : ValidatedProperties {
    override fun validate() {}

    companion object Factory {
        val DEFAULT =
            ImageProperties(
                preProcessing = PreProcessingProperties.DEFAULT,
                previews = setOf(),
            )

        fun create(
            preProcessing: PreProcessingProperties,
            lqip: Set<LQIPImplementation>,
        ) = validateAndCreate { ImageProperties(preProcessing, lqip) }

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: ImageProperties?,
        ): ImageProperties =
            create(
                preProcessing =
                    PreProcessingProperties.create(
                        applicationConfig?.tryGetConfig(PREPROCESSING),
                        parent?.preProcessing,
                    ),
                lqip =
                    applicationConfig
                        ?.tryGetStringList(LQIP)
                        ?.map { LQIPImplementation.valueOf(it.uppercase()) }
                        ?.toSet() ?: parent?.previews ?: setOf(),
            )
    }

    override fun toString(): String = "${this.javaClass.simpleName}(preProcessing: $preProcessing)"
}

class PreProcessingProperties private constructor(
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
) : ValidatedProperties {
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

    override fun validate() {
        maxWidth?.let {
            require(it > 0) { "'${MAX_WIDTH}' must be greater than 0" }
        }
        maxHeight?.let {
            require(it > 0) { "'${MAX_HEIGHT}' must be greater than 0" }
        }
        blur?.let {
            require(it in 0..150) { "'$BLUR' must be between 0 and 150" }
        }
        quality?.let {
            require(it in 1..100) { "'$QUALITY' must be between 1 and 100" }
        }
        pad?.let {
            require(it > 0) { "'$PAD' must be greater than 0" }
        }
        background?.let {
            require(it.isNotBlank() && it.length > 3 && it.startsWith('#')) { "'$BACKGROUND' must not be blank" }
        }
    }

    companion object Factory {
        val DEFAULT =
            PreProcessingProperties(
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
        ) = validateAndCreate {
            PreProcessingProperties(
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
        }

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PreProcessingProperties?,
        ) = create(
            maxWidth =
                applicationConfig
                    ?.tryGetString(MAX_WIDTH)
                    ?.toInt() ?: parent?.maxWidth,
            maxHeight =
                applicationConfig
                    ?.tryGetString(MAX_HEIGHT)
                    ?.toInt() ?: parent?.maxHeight,
            width =
                applicationConfig
                    ?.tryGetString(WIDTH)
                    ?.toInt() ?: parent?.width,
            height =
                applicationConfig
                    ?.tryGetString(HEIGHT)
                    ?.toInt() ?: parent?.height,
            format =
                applicationConfig
                    ?.tryGetString(IMAGE_FORMAT)
                    ?.let {
                        ImageFormat.fromFormat(it)
                    } ?: parent?.format,
            fit =
                applicationConfig
                    ?.tryGetString(FIT)
                    ?.let {
                        Fit.fromString(it)
                    } ?: parent?.fit ?: Fit.default,
            gravity =
                applicationConfig
                    ?.tryGetString(GRAVITY)
                    ?.let {
                        Gravity.fromString(it)
                    } ?: parent?.gravity ?: Gravity.default,
            rotate =
                applicationConfig
                    ?.tryGetString(ROTATE)
                    ?.let {
                        Rotate.fromString(it)
                    } ?: parent?.rotate ?: Rotate.default,
            flip =
                applicationConfig
                    ?.tryGetString(FLIP)
                    ?.let {
                        Flip.fromString(it)
                    } ?: parent?.flip ?: Flip.default,
            filter =
                applicationConfig
                    ?.tryGetString(FILTER)
                    ?.let {
                        Filter.fromString(it)
                    } ?: parent?.filter ?: Filter.default,
            blur =
                applicationConfig
                    ?.tryGetString(BLUR)
                    ?.toInt() ?: parent?.blur,
            quality =
                applicationConfig
                    ?.tryGetString(QUALITY)
                    ?.toInt() ?: parent?.quality,
            pad =
                applicationConfig
                    ?.tryGetString(PAD)
                    ?.toInt() ?: parent?.pad,
            background =
                applicationConfig?.tryGetString(BACKGROUND)
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

    override fun toString(): String =
        "${this.javaClass.simpleName}(maxWidth=$maxWidth, maxHeight=$maxHeight, imageFormat=$format " +
            "width=$width, height=$height, format=$format, fit=$fit, gravity=$gravity, rotate=$rotate, " +
            "flip=$flip, filter=$filter, blur=$blur, quality=$quality, pad=$pad, background=$background)"
}
