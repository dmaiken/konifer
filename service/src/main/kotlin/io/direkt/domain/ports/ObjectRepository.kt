package io.direkt.domain.ports

import io.direkt.asset.variant.AssetVariant
import io.direkt.image.model.ImageFormat
import io.ktor.utils.io.ByteWriteChannel
import java.io.File

interface ObjectRepository {
    suspend fun persist(
        bucket: String,
        asset: File,
        format: ImageFormat,
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
