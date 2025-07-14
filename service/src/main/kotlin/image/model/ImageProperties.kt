package image.model

import io.ktor.server.config.ApplicationConfig
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImageProperties.PREPROCESSING
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImageProperties.PreProcessingProperties.IMAGE_FORMAT
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImageProperties.PreProcessingProperties.MAX_HEIGHT
import io.properties.ConfigurationProperties.PathConfigurationProperties.ImageProperties.PreProcessingProperties.MAX_WIDTH
import io.properties.ValidatedProperties
import io.properties.validateAndCreate
import io.tryGetConfig

class ImageProperties private constructor(
    val preProcessing: PreProcessingProperties,
) : ValidatedProperties {
    override fun validate() {}

    companion object {
        fun create(preProcessing: PreProcessingProperties) = validateAndCreate { ImageProperties(preProcessing) }

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
            )

        fun default() =
            ImageProperties(
                preProcessing = PreProcessingProperties.default(),
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
) : ValidatedProperties {
    val enabled: Boolean = maxWidth != null || maxHeight != null || imageFormat != null

    override fun validate() {
        maxWidth?.let {
            require(it > 0) { "'${MAX_WIDTH}' must be greater than 0" }
        }
        maxHeight?.let {
            require(it > 0) { "'${MAX_HEIGHT}' must be greater than 0" }
        }
    }

    companion object {
        fun create(
            maxWidth: Int?,
            maxHeight: Int?,
            imageFormat: ImageFormat?,
        ) = validateAndCreate { PreProcessingProperties(maxWidth, maxHeight, imageFormat) }

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
        )

        fun default() =
            PreProcessingProperties(
                maxWidth = null,
                maxHeight = null,
                imageFormat = null,
            )
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(maxWidth=$maxWidth, maxHeight=$maxHeight, imageFormat=$imageFormat)"
    }
}
