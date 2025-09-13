package image.model

import io.asset.ManipulationParameters.FIT
import io.image.lqip.LQIPImplementation
import io.image.model.Fit
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
    val imageFormat: ImageFormat?,
    val fit: Fit,
) : ValidatedProperties {
    val enabled: Boolean = maxWidth != null || maxHeight != null || imageFormat != null || fit != Fit.SCALE

    override fun validate() {
        maxWidth?.let {
            require(it > 0) { "'${MAX_WIDTH}' must be greater than 0" }
        }
        maxHeight?.let {
            require(it > 0) { "'${MAX_HEIGHT}' must be greater than 0" }
        }
    }

    companion object {
        val DEFAULT =
            PreProcessingProperties(
                maxWidth = null,
                maxHeight = null,
                imageFormat = null,
                fit = Fit.SCALE,
            )

        fun create(
            maxWidth: Int?,
            maxHeight: Int?,
            imageFormat: ImageFormat?,
            fit: Fit = Fit.SCALE,
        ) = validateAndCreate { PreProcessingProperties(maxWidth, maxHeight, imageFormat, fit) }

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
            imageFormat =
                applicationConfig?.propertyOrNull(IMAGE_FORMAT)?.getString()
                    ?.let {
                        ImageFormat.fromFormat(it)
                    } ?: parent?.imageFormat,
            fit =
                applicationConfig?.propertyOrNull(FIT)?.getString()
                    ?.let {
                        Fit.fromString(it)
                    } ?: parent?.fit ?: Fit.SCALE,
        )
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(maxWidth=$maxWidth, maxHeight=$maxHeight, imageFormat=$imageFormat)"
    }
}
