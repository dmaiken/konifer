package io.direkt.domain.ports

import io.ktor.utils.io.ByteWriteChannel
import java.io.File
import java.time.LocalDateTime

interface ObjectRepository {
    suspend fun persist(
        bucket: String,
        key: String,
        file: File,
    ): LocalDateTime

    suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
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

    fun generateObjectUrl(
        bucket: String,
        key: String,
    ): String
}

data class FetchResult(
    val found: Boolean,
    val contentLength: Long,
) {
    companion object Factory {
        val NOT_FOUND = FetchResult(false, 0)

        fun found(contentLength: Long) = FetchResult(true, contentLength)
    }
}
