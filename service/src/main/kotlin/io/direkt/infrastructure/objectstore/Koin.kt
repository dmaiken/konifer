package io.direkt.infrastructure.objectstore

import aws.sdk.kotlin.services.s3.S3Client
import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.objectstore.filesystem.FileSystemObjectRepository
import io.direkt.infrastructure.objectstore.filesystem.FileSystemProperties
import io.direkt.infrastructure.objectstore.inmemory.InMemoryObjectRepository
import io.direkt.infrastructure.objectstore.s3.PresignedUrlProperties
import io.direkt.infrastructure.objectstore.s3.S3ClientProperties
import io.direkt.infrastructure.objectstore.s3.S3ObjectRepository
import io.direkt.infrastructure.objectstore.s3.s3Client
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.OBJECT_STORE
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.FILESYSTEM
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.FileSystemPropertyKeys.HTTP_PATH
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.FileSystemPropertyKeys.MOUNT_PATH
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.ACCESS_KEY
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.ENDPOINT_URL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.PRESIGN_URL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.PreSignedUrlPropertyKeys.ENABLED
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.PreSignedUrlPropertyKeys.TTL
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.REGION
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.SECRET_KEY
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.USE_PATH_STYLE
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.util.logging.KtorSimpleLogger
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Application.objectStoreModule(provider: ObjectStoreProvider): Module =
    module {
        val logger = KtorSimpleLogger("io.direkt.infrastructure.objectstore")
        val objectStoreConfig =
            environment.config
                .tryGetConfig(OBJECT_STORE)
        logger.info("Using object store provider: $provider")
        when (provider) {
            ObjectStoreProvider.IN_MEMORY -> {
                single<ObjectRepository> {
                    InMemoryObjectRepository()
                }
            }
            ObjectStoreProvider.S3 -> {
                val s3ConfigurationProperties = objectStoreConfig?.tryGetConfig(S3)
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
            ObjectStoreProvider.FILESYSTEM -> {
                val fileSystemProperties = objectStoreConfig?.tryGetConfig(FILESYSTEM)
                val properties =
                    FileSystemProperties(
                        mountPath =
                            fileSystemProperties
                                ?.tryGetString(MOUNT_PATH)
                                ?.removeSuffix("/")
                                ?: throw IllegalArgumentException(
                                    "Must supply ${OBJECT_STORE}.$FILESYSTEM.$MOUNT_PATH",
                                ),
                        httpPath =
                            fileSystemProperties
                                .tryGetString(HTTP_PATH)
                                ?.removeSuffix("/")
                                ?: throw IllegalArgumentException(
                                    "Must supply ${OBJECT_STORE}.$FILESYSTEM.$HTTP_PATH",
                                ),
                    )
                single<ObjectRepository> {
                    FileSystemObjectRepository(properties)
                }
            }
        }
    }
