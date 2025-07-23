package asset.store

import asset.variant.AssetVariant
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel

interface ObjectStore {
    suspend fun persist(
        asset: ByteChannel,
        contentLength: Long? = null,
    ): PersistResult

    suspend fun fetch(
        bucket: String,
        key: String,
        stream: ByteWriteChannel,
    ): FetchResult

    suspend fun exists(
        bucket: String,
        key: String,
    ): Boolean

    /**
     * Delete an object by key. This method is idempotent and will not throw an exception if the object does not exist
     */
    suspend fun delete(
        bucket: String,
        key: String,
    )

    suspend fun deleteAll(
        bucket: String,
        keys: List<String>,
    )

    fun generateObjectUrl(variant: AssetVariant): String
}

data class PersistResult(
    val key: String,
    val bucket: String,
)

data class FetchResult(
    val found: Boolean,
    val contentLength: Long,
) {
    companion object {
        fun notFound() = FetchResult(false, 0)

        fun found(contentLength: Long) = FetchResult(true, contentLength)
    }
}
