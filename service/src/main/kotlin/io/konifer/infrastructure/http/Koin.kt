package io.konifer.infrastructure.http

import io.konifer.infrastructure.HttpProperties
import io.konifer.infrastructure.HttpProperties.Factory.DEFAULT_PUBLIC_URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HTTP
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HttpPropertyKeys.PUBLIC_URL
import io.konifer.infrastructure.tryGetConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import okhttp3.ConnectionPool
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import java.util.concurrent.TimeUnit

fun httpClientModule(): Module =
    module {
        single<HttpClient> {
            HttpClient(OkHttp) {
                // Redirects are followed explicitly so every target can be validated against the source allowlist.
                followRedirects = false

                engine {
                    config {
                        connectionPool(
                            ConnectionPool(
                                maxIdleConnections = 100,
                                keepAliveDuration = 5,
                                timeUnit = TimeUnit.MINUTES,
                            ),
                        )

                        followRedirects(false)
                    }
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 5000
                    socketTimeoutMillis = 10000
                }

                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 1)
                    exponentialDelay()
                }
            }
        }
    }

fun Application.httpModule(): Module =
    module {
        single<HttpProperties> {
            HttpProperties(
                publicUrl =
                    environment.config
                        .tryGetConfig(HTTP)
                        ?.tryGetString(PUBLIC_URL)
                        ?: DEFAULT_PUBLIC_URL,
            )
        }

        single<AssetUrlGenerator>()
    }
