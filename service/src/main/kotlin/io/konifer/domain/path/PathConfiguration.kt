package io.konifer.domain.path

import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.ImageProperties
import io.konifer.domain.variant.preprocessing.PreProcessingProperties
import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetStringList

data class PathConfiguration(
    val allowedContentTypes: List<String>?,
    val preProcessing: PreProcessingProperties,
    val image: ImageProperties,
    val eagerVariants: List<String>,
    val objectStore: ObjectStoreProperties,
    val returnFormat: ReturnFormatProperties,
    val cacheControl: CacheControlProperties,
) {
    init {
        validate()
    }

    companion object {
        val default =
            PathConfiguration(
                allowedContentTypes = null,
                preProcessing = PreProcessingProperties.default,
                image = ImageProperties.default,
                eagerVariants = emptyList(),
                objectStore = ObjectStoreProperties.default,
                returnFormat = ReturnFormatProperties.default,
                cacheControl = CacheControlProperties.default,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PathConfiguration? = null,
        ): PathConfiguration {
            if (applicationConfig == null) {
                return default
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
                objectStore =
                    ObjectStoreProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.OBJECT_STORE),
                        parent = parent?.objectStore,
                    ),
                returnFormat =
                    ReturnFormatProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.RETURN_FORMAT),
                        parent = parent?.returnFormat,
                    ),
                cacheControl =
                    CacheControlProperties.create(
                        applicationConfig = applicationConfig.tryGetConfig(ConfigurationPropertyKeys.PathPropertyKeys.CACHE_CONTROL),
                        parent = parent?.cacheControl,
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
