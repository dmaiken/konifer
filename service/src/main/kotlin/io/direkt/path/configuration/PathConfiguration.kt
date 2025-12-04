package io.direkt.path.configuration

import io.direkt.image.model.ImageFormat
import io.direkt.image.model.ImageProperties
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.ALLOWED_CONTENT_TYPES
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.EAGER_VARIANTS
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.IMAGE
import io.direkt.properties.ConfigurationProperties.PathConfigurationProperties.S3
import io.direkt.properties.ValidatedProperties
import io.direkt.properties.validateAndCreate
import io.direkt.s3.S3PathProperties
import io.direkt.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList

class PathConfiguration private constructor(
    val allowedContentTypes: List<String>?,
    val imageProperties: ImageProperties,
    val eagerVariants: List<String>,
    val s3PathProperties: S3PathProperties,
) : ValidatedProperties {
    companion object {
        val DEFAULT =
            PathConfiguration(
                allowedContentTypes = null,
                imageProperties = ImageProperties.DEFAULT,
                eagerVariants = emptyList(),
                s3PathProperties = S3PathProperties.DEFAULT,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PathConfiguration? = null,
        ): PathConfiguration {
            if (applicationConfig == null) {
                return DEFAULT
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
                eagerVariants =
                    applicationConfig.tryGetStringList(EAGER_VARIANTS)
                        ?: parent?.eagerVariants ?: emptyList(),
                s3PathProperties =
                    S3PathProperties.create(
                        applicationConfig.tryGetConfig(S3),
                        parent?.s3PathProperties,
                    ),
            )
        }

        fun create(
            allowedContentTypes: List<String>?,
            imageProperties: ImageProperties,
            eagerVariants: List<String>,
            s3PathProperties: S3PathProperties,
        ): PathConfiguration =
            validateAndCreate {
                PathConfiguration(
                    allowedContentTypes = allowedContentTypes,
                    imageProperties = imageProperties,
                    eagerVariants = eagerVariants,
                    s3PathProperties = s3PathProperties,
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

    override fun toString(): String =
        "${this.javaClass.simpleName}(allowedContentTypes=$allowedContentTypes, imageProperties=$imageProperties)"
}
