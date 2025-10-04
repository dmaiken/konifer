package image.model

import io.asset.ManipulationParameters.FILTER
import io.asset.ManipulationParameters.FIT
import io.asset.ManipulationParameters.FLIP
import io.asset.ManipulationParameters.HEIGHT
import io.asset.ManipulationParameters.ROTATE
import io.asset.ManipulationParameters.WIDTH
import io.image.lqip.LQIPImplementation
import io.image.model.Filter
import io.image.model.Fit
import io.image.model.Flip
import io.image.model.Rotate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.LQIP
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PREPROCESSING
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.IMAGE_FORMAT
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_HEIGHT
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImagePropertyKeys.PreProcessingPropertyKeys.MAX_WIDTH
import io.properties.ValidatedProperties
import io.properties.validateAndCreate
import io.tryGetConfig

class ImageProperties private constructor(
    val preProcessing: PreProcessingProperties,
    val previews: Set<LQIPImplementation>,
) : ValidatedProperties {
    override fun validate() {}

    companion object {
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
                    applicationConfig?.tryGetStringList(LQIP)
                        ?.map { LQIPImplementation.valueOf(it.uppercase()) }
                        ?.toSet() ?: parent?.previews ?: setOf(),
            )
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(preProcessing: $preProcessing)"
    }
}

class PreProcessingProperties private constructor(
    val maxWidth: Int?,
    val maxHeight: Int?,
    val width: Int?,
    val height: Int?,
    val imageFormat: ImageFormat?,
    val fit: Fit,
    val rotate: Rotate,
    val flip: Flip,
    val filter: Filter,
) : ValidatedProperties {
    val enabled: Boolean =
        maxWidth != null || maxHeight != null || imageFormat != null ||
            fit != Fit.default || rotate != Rotate.default || flip != Flip.default

    override fun validate() {
        maxWidth?.let {
            require(it > 0) { "'${MAX_WIDTH}' must be greater than 0" }
        }
        maxHeight?.let {
            require(it > 0) { "'${MAX_HEIGHT}' must be greater than 0" }
        }
    }

    companion object Factory {
        val DEFAULT =
            PreProcessingProperties(
                maxWidth = null,
                maxHeight = null,
                width = null,
                height = null,
                imageFormat = null,
                fit = Fit.default,
                rotate = Rotate.default,
                flip = Flip.default,
                filter = Filter.default,
            )

        fun create(
            maxWidth: Int?,
            maxHeight: Int?,
            width: Int?,
            height: Int?,
            format: ImageFormat?,
            fit: Fit,
            rotate: Rotate,
            flip: Flip,
            filter: Filter,
        ) = validateAndCreate { PreProcessingProperties(maxWidth, maxHeight, width, height, format, fit, rotate, flip, filter) }

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PreProcessingProperties?,
        ) = create(
            maxWidth =
                applicationConfig?.propertyOrNull(MAX_WIDTH)?.getString()
                    ?.toInt() ?: parent?.maxWidth,
            maxHeight =
                applicationConfig?.propertyOrNull(MAX_HEIGHT)?.getString()
                    ?.toInt() ?: parent?.maxHeight,
            width =
                applicationConfig?.propertyOrNull(WIDTH)?.getString()
                    ?.toInt() ?: parent?.width,
            height =
                applicationConfig?.propertyOrNull(HEIGHT)?.getString()
                    ?.toInt() ?: parent?.height,
            format =
                applicationConfig?.propertyOrNull(IMAGE_FORMAT)?.getString()
                    ?.let {
                        ImageFormat.fromFormat(it)
                    } ?: parent?.imageFormat,
            fit =
                applicationConfig?.propertyOrNull(FIT)?.getString()
                    ?.let {
                        Fit.fromString(it)
                    } ?: parent?.fit ?: Fit.default,
            rotate =
                applicationConfig?.propertyOrNull(ROTATE)?.getString()
                    ?.let {
                        Rotate.fromString(it)
                    } ?: parent?.rotate ?: Rotate.default,
            flip =
                applicationConfig?.propertyOrNull(FLIP)?.getString()
                    ?.let {
                        Flip.fromString(it)
                    } ?: parent?.flip ?: Flip.default,
            filter =
                applicationConfig?.propertyOrNull(FILTER)?.getString()
                    ?.let {
                        Filter.fromString(it)
                    } ?: parent?.filter ?: Filter.default,
        )
    }

    val requestedImageTransformation by lazy { toRequestedImageTransformation() }

    private fun toRequestedImageTransformation(): RequestedImageTransformation =
        RequestedImageTransformation(
            width = width ?: maxWidth,
            height = height ?: maxHeight,
            format = imageFormat,
            fit = fit,
            rotate = rotate,
            flip = flip,
            canUpscale = maxWidth == null && maxHeight == null,
            filter = filter,
        )

    override fun toString(): String {
        return "${this.javaClass.simpleName}(maxWidth=$maxWidth, maxHeight=$maxHeight, imageFormat=$imageFormat)"
    }
}
