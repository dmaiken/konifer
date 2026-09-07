package io.konifer.domain.ports

import io.konifer.common.image.ImageFormat
import io.ktor.http.Url
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.time.Duration

interface ObjectStore {
    suspend fun persist(
        request: PersistObjectStoreRequest,
        channel: ByteChannel,
    ): LocalDateTime

    suspend fun persist(
        request: PersistObjectStoreRequest,
        file: Path,
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

    /**
     * Generate a presigned URL if supported. If not supported, [PresignedUrl.NotSupported] is returned.
     */
    suspend fun generatePresignedUrl(
        bucket: String,
        key: String,
        ttl: Duration,
    ): PresignedUrl
}

sealed interface PresignedUrl {
    data class Supported(
        val url: Url,
        val expiresAt: LocalDateTime?,
    ) : PresignedUrl

    object NotSupported : PresignedUrl
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

data class PersistObjectStoreRequest(
    val bucket: String,
    val key: String,
    val contentType: ImageFormat,
)
