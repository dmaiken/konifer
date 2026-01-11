package io.direkt.domain.path

import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.ImageProperties
import io.direkt.domain.variant.preprocessing.PreProcessingProperties
import io.direkt.infrastructure.objectstore.s3.S3PathProperties
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList

data class PathConfiguration(
    val allowedContentTypes: List<String>?,
    val preProcessing: PreProcessingProperties,
    val image: ImageProperties,
    val eagerVariants: List<String>,
    val s3Path: S3PathProperties,
) {
    init {
        validate()
    }

    companion object {
        val DEFAULT =
            PathConfiguration(
                allowedContentTypes = null,
                preProcessing = PreProcessingProperties.DEFAULT,
                image = ImageProperties.DEFAULT,
                eagerVariants = emptyList(),
                s3Path = S3PathProperties.DEFAULT,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PathConfiguration? = null,
        ): PathConfiguration {
            if (applicationConfig == null) {
                return DEFAULT
            }
            return PathConfiguration(
                allowedContentTypes =
                    applicationConfig.tryGetStringList(ConfigurationPropertyKeys.PathPropertyKeys.ALLOWED_CONTENT_TYPES)
                        ?: parent?.allowedContentTypes,
                preProcessing =
                    PreProcessingProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.PREPROCESSING),
                        parent = parent?.preProcessing,
                    ),
                image =
                    ImageProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.IMAGE),
                        parent = parent?.image,
                    ),
                eagerVariants =
                    applicationConfig.tryGetStringList(ConfigurationPropertyKeys.PathPropertyKeys.EAGER_VARIANTS)
                        ?: parent?.eagerVariants ?: emptyList(),
                s3Path =
                    S3PathProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.S3),
                        parent = parent?.s3Path,
                    ),
            )
        }
    }

    private fun validate() {
        allowedContentTypes?.let { allowedContentTypes ->
            val supportedContentTypes = ImageFormat.entries.map { it.mimeType }
            allowedContentTypes.forEach { allowedContentType ->
                require(supportedContentTypes.contains(allowedContentType)) {
                    "$allowedContentType is not a supported content type"
                }
            }
        }
    }
}
