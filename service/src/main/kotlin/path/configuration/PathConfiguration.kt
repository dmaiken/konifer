package io.path.configuration

import image.model.ImageFormat
import image.model.ImageProperties
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList
import io.properties.ConfigurationProperties.PathConfigurationProperties.ALLOWED_CONTENT_TYPES
import io.properties.ConfigurationProperties.PathConfigurationProperties.IMAGE
import io.properties.ValidatedProperties
import io.properties.validateAndCreate
import io.tryGetConfig

class PathConfiguration private constructor(
    val allowedContentTypes: List<String>?,
    val imageProperties: ImageProperties,
) : ValidatedProperties {
    companion object {
        val DEFAULT =
            PathConfiguration(
                allowedContentTypes = null,
                imageProperties = ImageProperties.default(),
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PathConfiguration? = null,
        ): PathConfiguration {
            if (applicationConfig == null) {
                return default()
            }
            return create(
                allowedContentTypes =
                    applicationConfig.tryGetStringList(ALLOWED_CONTENT_TYPES)
                        ?: parent?.allowedContentTypes,
                imageProperties =
                    ImageProperties.create(
                        applicationConfig.tryGetConfig(IMAGE),
                        parent?.imageProperties,
                    ),
            )
        }

        fun create(
            allowedContentTypes: List<String>?,
            imageProperties: ImageProperties,
        ): PathConfiguration =
            validateAndCreate {
                PathConfiguration(
                    allowedContentTypes = allowedContentTypes,
                    imageProperties = imageProperties,
                )
            }

        fun default(): PathConfiguration {
            return PathConfiguration(
                allowedContentTypes = null,
                imageProperties = ImageProperties.default(),
            )
        }
    }

    override fun validate() {
        allowedContentTypes?.let { allowedContentTypes ->
            val supportedContentTypes = ImageFormat.entries.map { it.mimeType }
            allowedContentTypes.forEach { allowedContentType ->
                require(supportedContentTypes.contains(allowedContentType)) {
                    "$allowedContentType is not a supported content type"
                }
            }
        }
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(allowedContentTypes=$allowedContentTypes, imageProperties=$imageProperties)"
    }
}
