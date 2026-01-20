package io.konifer.domain.variant.preprocessing

import io.konifer.infrastructure.properties.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.IMAGE
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig

data class PreProcessingProperties(
    val image: ImagePreProcessingProperties,
) {
    companion object Factory {
        val DEFAULT =
            PreProcessingProperties(
                image = ImagePreProcessingProperties.DEFAULT,
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
