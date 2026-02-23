package io.konifer.infrastructure.objectstore

import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.filesystem.FileSystemObjectStore
import io.konifer.infrastructure.objectstore.filesystem.FileSystemProperties
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.konifer.infrastructure.objectstore.s3.S3ClientProperties
import io.konifer.infrastructure.objectstore.s3.S3ObjectStore
import io.konifer.infrastructure.objectstore.s3.s3Client
import io.konifer.infrastructure.objectstore.s3.s3Presigner
import io.konifer.infrastructure.objectstore.s3.s3TransferManager
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.OBJECT_STORE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.FILESYSTEM
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.FileSystemPropertyKeys.MOUNT_PATH
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.ACCESS_KEY
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.ENDPOINT_URL
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.FORCE_PATH_STYLE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.REGION
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.S3PropertyKeys.SECRET_KEY
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.util.logging.KtorSimpleLogger
import org.koin.core.module.Module
import org.koin.dsl.module
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager

fun Application.objectStoreModule(provider: ObjectStoreProvider): Module =
    module {
        val logger = KtorSimpleLogger("io.konifer.infrastructure.objectstore")
        val objectStoreConfig =
            environment.config
                .tryGetConfig(OBJECT_STORE)
        logger.info("Using object store provider: $provider")
        when (provider) {
            ObjectStoreProvider.IN_MEMORY -> {
                single<ObjectStore> {
                    InMemoryObjectStore()
                }
            }
            ObjectStoreProvider.S3 -> {
                val s3ConfigurationProperties = objectStoreConfig?.tryGetConfig(S3)
                val s3ClientProperties =
                    S3ClientProperties(
                        endpointUrl = s3ConfigurationProperties?.tryGetString(ENDPOINT_URL),
                        accessKey = s3ConfigurationProperties?.tryGetString(ACCESS_KEY),
                        secretKey = s3ConfigurationProperties?.tryGetString(SECRET_KEY),
                        region = s3ConfigurationProperties?.tryGetString(REGION),
                        forcePathStyle = s3ConfigurationProperties?.tryGetString(FORCE_PATH_STYLE)?.toBoolean() ?: false,
                    )
                single<S3AsyncClient>(createdAtStart = true) {
                    s3Client(s3ClientProperties)
                }
                single<S3TransferManager>(createdAtStart = true) {
                    s3TransferManager(get())
                }
                single<S3Presigner> {
                    s3Presigner(get())
                }
                single<ObjectStore> {
                    S3ObjectStore(get(), get(), get())
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
                    )
                single<ObjectStore> {
                    FileSystemObjectStore(properties)
                }
            }
        }
    }
