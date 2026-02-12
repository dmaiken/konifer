package io.konifer.domain.path

import io.konifer.infrastructure.property.ConfigurationPropertyKeys
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.config.ApplicationConfig

data class ReturnFormatProperties(
    val redirect: RedirectProperties = RedirectProperties.default,
) {
    companion object Factory {
        val default = ReturnFormatProperties()

        fun create(
            applicationConfig: ApplicationConfig?,
            parent: ReturnFormatProperties?,
        ): ReturnFormatProperties =
            ReturnFormatProperties(
                redirect =
                    RedirectProperties.create(
                        applicationConfig =
                            applicationConfig?.tryGetConfig(
                                ConfigurationPropertyKeys.PathPropertyKeys.ReturnFormatPropertyKeys.REDIRECT,
                            ),
                        parent = parent?.redirect,
                    ),
            )
    }
}
