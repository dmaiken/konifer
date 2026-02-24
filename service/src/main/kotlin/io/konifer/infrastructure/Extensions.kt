package io.konifer.infrastructure

import com.typesafe.config.ConfigException
import io.ktor.server.config.ApplicationConfig
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer

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
