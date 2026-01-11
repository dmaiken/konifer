package io.direkt.infrastructure.objectstore

import aws.sdk.kotlin.services.s3.S3Client
import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.objectstore.inmemory.InMemoryObjectRepository
import io.direkt.infrastructure.objectstore.s3.PresignedUrlProperties
import io.direkt.infrastructure.objectstore.s3.S3ClientProperties
import io.direkt.infrastructure.objectstore.s3.S3ObjectRepository
import io.direkt.infrastructure.objectstore.s3.s3Client
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.OBJECT_STORE
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.ACCESS_KEY
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.ENDPOINT_URL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.PRESIGN_URL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.PreSignedUrlPropertyKeys.ENABLED
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.PreSignedUrlPropertyKeys.TTL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.REGION
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.SECRET_KEY
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectStorePropertyKeys.S3PropertyKeys.USE_PATH_STYLE
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Application.objectStoreModule(provider: ObjectStoreProvider): Module =
    module {
        when (provider) {
            ObjectStoreProvider.IN_MEMORY -> {
                single<ObjectRepository> {
                    InMemoryObjectRepository()
                }
            }
            ObjectStoreProvider.S3 -> {
                val s3ConfigurationProperties =
                    environment.config
                        .tryGetConfig(OBJECT_STORE)
                        ?.tryGetConfig(S3)
                val presignedUrlProperties =
                    s3ConfigurationProperties
                        ?.tryGetConfig(PRESIGN_URL)
                        ?.takeIf { it.tryGetString(ENABLED)?.toBoolean() ?: false }
                        ?.let { properties ->
                            PresignedUrlProperties(
                                ttl =
                                    properties
                                        .tryGetString(TTL)
                                        ?.let { Duration.parse(it) }
                                        ?: 30.minutes,
                            )
                        }
                val s3ClientProperties =
                    S3ClientProperties(
                        endpointUrl = s3ConfigurationProperties?.tryGetString(ENDPOINT_URL),
                        accessKey = s3ConfigurationProperties?.tryGetString(ACCESS_KEY),
                        secretKey = s3ConfigurationProperties?.tryGetString(SECRET_KEY),
                        region = s3ConfigurationProperties?.tryGetString(REGION),
                        presignedUrlProperties = presignedUrlProperties,
                        usePathStyleUrl =
                            s3ConfigurationProperties
                                ?.tryGetString(USE_PATH_STYLE)
                                ?.toBoolean()
                                ?: false,
                    )
                single<S3Client>(createdAtStart = true) {
                    s3Client(s3ClientProperties)
                }
                single<ObjectRepository> {
                    S3ObjectRepository(get(), s3ClientProperties)
                }
            }
        }
    }
