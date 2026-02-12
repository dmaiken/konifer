package io.konifer.infrastructure.asset

import io.konifer.domain.asset.MAX_BYTES_DEFAULT
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SOURCE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.UrlConfigurationPropertyKeys.ALLOWED_DOMAINS
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.UrlConfigurationPropertyKeys.MAX_BYTES
import io.konifer.infrastructure.tryGetConfig
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
