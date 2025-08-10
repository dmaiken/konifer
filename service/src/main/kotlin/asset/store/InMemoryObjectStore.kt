package asset.store

import asset.variant.AssetVariant
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import java.util.UUID

class InMemoryObjectStore() : ObjectStore {
    companion object {
        const val DEFAULT_PORT = 8080
    }

    private val store = mutableMapOf<String, MutableMap<String, ByteArray>>()

    override suspend fun persist(
        bucket: String,
        asset: ByteChannel,
        contentLength: Long?,
    ): PersistResult {
        val key = UUID.randomUUID().toString()
        store.computeIfAbsent(bucket) { mutableMapOf() }
        store[bucket]?.put(key, asset.toByteArray())

        return PersistResult(
            key = key,
            bucket = bucket,
        )
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
            } ?: FetchResult.notFound()
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

    override fun generateObjectUrl(variant: AssetVariant): String {
        return "http://localhost:$DEFAULT_PORT/objectStore/${variant.objectStoreBucket}" +
            "/${variant.objectStoreKey}"
    }

    fun clearObjectStore() {
        store.clear()
    }
}
