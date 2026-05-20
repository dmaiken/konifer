package io.konifer.infrastructure

import com.typesafe.config.ConfigException
import io.konifer.infrastructure.datastore.DataStoreProvider
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DATASTORE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PROVIDER
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.OBJECT_STORE
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.ObjectRepositoryPropertyKeys.PROVIDER as OBJECT_STORE_PROVIDER

fun ApplicationConfig.tryGetConfig(path: String): ApplicationConfig? =
    try {
        this.config(path)
    } catch (_: ConfigException) {
        null
    }

fun ApplicationConfig.tryGetConfigList(path: String): List<ApplicationConfig> =
    try {
        this.configList(path)
    } catch (_: ConfigException) {
        emptyList()
    }

fun ByteChannel.consumeAsFlow(): Flow<ByteBuffer> =
    flow {
        while (!isClosedForRead) {
            val buffer = ByteBuffer.allocate(8192) // 8KB buffer size
            val bytesRead = readAvailable(buffer)
            if (bytesRead > 0) {
                buffer.flip()
                emit(buffer) // Emit the buffer to the flow
            }
        }
    }

fun ApplicationConfig.getDataStoreProvider(): DataStoreProvider =
    System
        .getenv(EnvironmentVariable.IN_MEMORY.name)
        ?.takeIf { it == "true" }
        ?.let { DataStoreProvider.IN_MEMORY }
        ?: this
            .tryGetConfig(DATASTORE)
            ?.tryGetString(PROVIDER)
            ?.let {
                DataStoreProvider.fromConfig(it)
            }
        ?: DataStoreProvider.default

fun ApplicationConfig.getObjectStoreProvider(): ObjectStoreProvider =
    System
        .getenv(EnvironmentVariable.IN_MEMORY.name)
        ?.takeIf { it == "true" }
        ?.let { ObjectStoreProvider.IN_MEMORY }
        ?: this
            .tryGetConfig(OBJECT_STORE)
            ?.tryGetString(OBJECT_STORE_PROVIDER)
            ?.let {
                ObjectStoreProvider.fromConfig(it)
            }
        ?: ObjectStoreProvider.default
