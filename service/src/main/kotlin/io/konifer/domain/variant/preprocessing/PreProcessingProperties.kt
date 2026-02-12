package io.konifer.domain.variant.preprocessing

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.PathPropertyKeys.ImagePropertyKeys.PreProcessingPropertyKeys.IMAGE
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

data class PreProcessingProperties(
    val enabled: Boolean,
    val image: ImagePreProcessingProperties,
) {
    companion object Factory {
        val default =
            PreProcessingProperties(
                enabled = false,
                image = ImagePreProcessingProperties.default,
            )

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: PreProcessingProperties?,
        ): PreProcessingProperties =
            create(
                enabled =
                    applicationConfig
                        ?.tryGetString(PreProcessingPropertyKeys.ENABLED)
                        ?.toBoolean() ?: parent?.enabled ?: false,
                image =
                    ImagePreProcessingProperties.create(
                        applicationConfig = applicationConfig?.tryGetConfig(IMAGE),
                        parent = parent?.image,
                    ),
            )

        fun create(
            enabled: Boolean,
            image: ImagePreProcessingProperties,
        ): PreProcessingProperties =
            PreProcessingProperties(
                enabled = enabled,
                image = image,
            )
    }
}
