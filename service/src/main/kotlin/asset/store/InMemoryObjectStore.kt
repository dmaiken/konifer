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
        const val BUCKET = "assets"
    }

    private val store = mutableMapOf<String, ByteArray>()

    override suspend fun persist(
        asset: ByteChannel,
        contentLength: Long?,
    ): PersistResult {
        val key = UUID.randomUUID().toString()
        store.put(key, asset.toByteArray())

        return PersistResult(
            key = key,
            bucket = BUCKET,
        )
    }

    override suspend fun fetch(
        bucket: String,
        key: String,
        stream: ByteWriteChannel,
    ): FetchResult {
        if (bucket != BUCKET) {
            return FetchResult.notFound().also {
                stream.flushAndClose()
            }
        }
        return store[key]?.let {
            stream.writeFully(it)
            stream.flushAndClose()
            FetchResult(
                found = true,
                contentLength = it.size.toLong(),
            )
        } ?: FetchResult.notFound().also {
            stream.flushAndClose()
        }
    }

    override suspend fun delete(
        bucket: String,
        key: String,
    ) {
        if (bucket != BUCKET) {
            return
        }
        store.remove(key)
    }

    override suspend fun deleteAll(
        bucket: String,
        keys: List<String>,
    ) {
        if (bucket != BUCKET) {
            return
        }
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
