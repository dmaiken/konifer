package io.path.configuration

import io.aws.S3Properties
import io.image.model.ImageFormat
import io.image.model.ImageProperties
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList
import io.properties.ConfigurationProperties.PathConfigurationProperties.ALLOWED_CONTENT_TYPES
import io.properties.ConfigurationProperties.PathConfigurationProperties.EAGER_VARIANTS
import io.properties.ConfigurationProperties.PathConfigurationProperties.IMAGE
import io.properties.ConfigurationProperties.PathConfigurationProperties.S3
import io.properties.ValidatedProperties
import io.properties.validateAndCreate
import io.tryGetConfig

class PathConfiguration private constructor(
    val allowedContentTypes: List<String>?,
    val imageProperties: ImageProperties,
    val eagerVariants: List<String>,
    val s3Properties: S3Properties,
) : ValidatedProperties {
    companion object {
        val DEFAULT =
            PathConfiguration(
                allowedContentTypes = null,
                imageProperties = ImageProperties.DEFAULT,
                eagerVariants = emptyList(),
                s3Properties = S3Properties.DEFAULT,
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
                s3Properties =
                    S3Properties.create(
                        applicationConfig.tryGetConfig(S3),
                        parent?.s3Properties,
                    ),
            )
        }

        fun create(
            allowedContentTypes: List<String>?,
            imageProperties: ImageProperties,
            eagerVariants: List<String>,
            s3Properties: S3Properties,
        ): PathConfiguration =
            validateAndCreate {
                PathConfiguration(
                    allowedContentTypes = allowedContentTypes,
                    imageProperties = imageProperties,
                    eagerVariants = eagerVariants,
                    s3Properties = s3Properties,
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
