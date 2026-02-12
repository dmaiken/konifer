package io.konifer.domain.variant.preprocessing

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.IMAGE
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig

data class PreProcessingProperties(
    val image: ImagePreProcessingProperties,
) {
    companion object Factory {
        val default =
            PreProcessingProperties(
                image = ImagePreProcessingProperties.default,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PreProcessingProperties?,
        ): PreProcessingProperties =
            create(
                image =
                    ImagePreProcessingProperties.create(
                        applicationConfig = applicationConfig?.tryGetConfig(IMAGE),
                        parent = parent?.image,
                    ),
            )

        fun create(image: ImagePreProcessingProperties): PreProcessingProperties =
            PreProcessingProperties(
                image = image,
            )
    }
}
