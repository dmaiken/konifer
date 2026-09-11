package io.konifer.infrastructure.asset

import io.konifer.domain.ByteSize
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.infrastructure.HttpProperties
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.konifer.infrastructure.http.bodylimit.DEFAULT_UPLOAD_BODY_LIMIT
import io.konifer.infrastructure.objectstore.s3.AwsS3SourceReader
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HTTP
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.HttpPropertyKeys.PUBLIC_URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SOURCE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.UrlConfigurationPropertyKeys.ALLOWED_DOMAINS
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.SourceConfigurationPropertyKeys.UrlConfigurationPropertyKeys.MAX_BYTES
import io.konifer.infrastructure.tryGetConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import okhttp3.ConnectionPool
import org.koin.core.error.InstanceCreationException
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.util.concurrent.TimeUnit

private val awsS3SourceClient = named("awsS3SourceClient")

fun Application.externalSourceModule(): Module =
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
        single<S3AsyncClient>(awsS3SourceClient) {
            S3AsyncClient
                .builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .crossRegionAccessEnabled(true)
                .build()
        }
        single<AwsS3SourceReader> {
            AwsS3SourceReader(
                s3Client =
                    lazy {
                        try {
                            get<S3AsyncClient>(awsS3SourceClient)
                        } catch (cause: InstanceCreationException) {
                            throw AssetSourceUnavailableException(
                                message = "Amazon S3 source client could not be initialized",
                                cause = cause.cause ?: cause,
                            )
                        }
                    },
            )
        }
        single<AssetContainerFactory> {
            val maxContentLength =
                environment.config
                    .tryGetConfig(SOURCE)
                    ?.tryGetConfig(URL)
                    ?.tryGetString(MAX_BYTES)
                    ?.let { ByteSize.parse(it) }
                    ?: DEFAULT_UPLOAD_BODY_LIMIT
            val allowedDomains =
                environment.config
                    .tryGetConfig(SOURCE)
                    ?.tryGetConfig(URL)
                    ?.tryGetStringList(ALLOWED_DOMAINS)
                    ?.toSet()
                    ?: emptySet()

            ExternalSourceContainerFactory(
                maxBytes = maxContentLength,
                httpClient = get(),
                awsS3SourceReader = get(),
                allowedDomains = allowedDomains,
            )
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
                        ?.let(::Url),
            )
        }

        single<AssetUrlGenerator>()
    }
