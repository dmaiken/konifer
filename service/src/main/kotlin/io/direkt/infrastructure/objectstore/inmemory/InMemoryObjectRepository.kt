package io.direkt.infrastructure.objectstore.inmemory

import io.direkt.domain.ports.FetchResult
import io.direkt.domain.ports.ObjectRepository
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import java.io.File
import java.time.LocalDateTime

class InMemoryObjectRepository : ObjectRepository {
    companion object {
        const val DEFAULT_PORT = 8080
    }

    private val store = mutableMapOf<String, MutableMap<String, ByteArray>>()

    override suspend fun persist(
        bucket: String,
        key: String,
        file: File,
    ): LocalDateTime {
        store.computeIfAbsent(bucket) { mutableMapOf() }

        store[bucket]?.put(key, file.readChannel().toByteArray())

        return LocalDateTime.now()
    }

    override suspend fun fetch(
        bucket: String,
        key: String,
        stream: ByteWriteChannel,
    ): FetchResult =
        try {
            store[bucket]?.get(key)?.let {
                stream.writeFully(it)
                FetchResult(
                    found = true,
                    contentLength = it.size.toLong(),
                )
            } ?: FetchResult.NOT_FOUND
        } finally {
            stream.flushAndClose()
        }

    override suspend fun exists(
        bucket: String,
        key: String,
    ): Boolean = store[bucket]?.contains(key) == true

    override suspend fun delete(
        bucket: String,
        key: String,
    ) {
        store[bucket]?.remove(key)
    }

    override suspend fun deleteAll(
        bucket: String,
        keys: List<String>,
    ) {
        keys.forEach { delete(bucket, it) }
    }

    override fun generateObjectUrl(
        bucket: String,
        key: String,
    ): String = "http://localhost:$DEFAULT_PORT/objectStore/$bucket/$key"

    fun clearObjectStore() {
        store.clear()
    }
}
