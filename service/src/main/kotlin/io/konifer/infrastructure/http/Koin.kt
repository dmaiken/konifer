package io.konifer.infrastructure.http

import io.konifer.infrastructure.HttpProperties
import io.konifer.infrastructure.HttpProperties.Factory.DEFAULT_PUBLIC_URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HTTP
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HttpPropertyKeys.PUBLIC_URL
import io.konifer.infrastructure.tryGetConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.http.HttpClient as JavaHttpClient

fun httpClientModule(): Module =
    module {
        single<HttpClient> {
            HttpClient(Java) {
                engine {
                    dispatcher = Dispatchers.IO
                    pipelining = true
                    protocolVersion = JavaHttpClient.Version.HTTP_2
                }
            }
        }
    }

fun Application.httpModule(): Module =
    module {
        single<AssetUrlGenerator> {
            AssetUrlGenerator(8080)
        }

        single<HttpProperties> {
            HttpProperties(
                publicUrl =
                    environment.config
                        .tryGetConfig(HTTP)
                        ?.tryGetString(PUBLIC_URL)
                        ?: DEFAULT_PUBLIC_URL,
            )
        }
    }
