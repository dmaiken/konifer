package io.direkt.infrastructure.asset

import io.direkt.asset.MAX_BYTES_DEFAULT
import io.direkt.domain.ports.AssetContainerFactory
import io.direkt.infrastructure.properties.ConfigurationProperties.SOURCE
import io.direkt.infrastructure.properties.ConfigurationProperties.SourceConfigurationProperties.URL
import io.direkt.infrastructure.properties.ConfigurationProperties.SourceConfigurationProperties.UrlConfigurationProperties.ALLOWED_DOMAINS
import io.direkt.infrastructure.properties.ConfigurationProperties.SourceConfigurationProperties.UrlConfigurationProperties.MAX_BYTES
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.assetContainerFactoryModule(): Module =
    module {
        single<AssetContainerFactory> {
            val allowedDomains =
                environment.config
                    .tryGetConfig(SOURCE)
                    ?.tryGetConfig(URL)
                    ?.tryGetStringList(ALLOWED_DOMAINS)
                    ?.toSet()
                    ?: emptySet()
            val maxContentLength =
                environment.config
                    .tryGetConfig(SOURCE)
                    ?.tryGetConfig(URL)
                    ?.tryGetString(MAX_BYTES)
                    ?.toLong()
                    ?: MAX_BYTES_DEFAULT

            AssetStreamContainerFactory(allowedDomains, maxContentLength, get())
        }
    }
