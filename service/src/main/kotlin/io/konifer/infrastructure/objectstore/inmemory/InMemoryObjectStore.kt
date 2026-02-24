package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.domain.path.RedirectProperties
import io.konifer.domain.path.RedirectStrategy
import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.ObjectStore
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import java.time.LocalDateTime

class InMemoryObjectStore : ObjectStore {
    private val store = mutableMapOf<String, MutableMap<String, ByteArray>>()

    override suspend fun persist(
        bucket: String,
        key: String,
        channel: ByteChannel,
    ): LocalDateTime {
        store.computeIfAbsent(bucket) { mutableMapOf() }

        store[bucket]?.put(key, channel.toByteArray())

        return LocalDateTime.now()
    }

    override suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
    ): FetchResult =
        try {
            store[bucket]?.get(key)?.let {
                channel.writeFully(it)
                FetchResult(
                    found = true,
                    contentLength = it.size.toLong(),
                )
            } ?: FetchResult.NOT_FOUND
        } finally {
            channel.flushAndClose()
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

    override suspend fun generateObjectUrl(
        bucket: String,
        key: String,
        properties: RedirectProperties,
    ): String? =
        when (properties.strategy) {
            RedirectStrategy.TEMPLATE ->
                properties.template.resolve(
                    bucket = bucket,
                    key = key,
                )
            RedirectStrategy.PRESIGNED, RedirectStrategy.NONE -> null
        }

    fun clearObjectStore() {
        store.clear()
    }
}
