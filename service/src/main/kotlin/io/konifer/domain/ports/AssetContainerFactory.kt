package io.konifer.domain.ports

import io.konifer.domain.asset.AssetDataContainer
import java.net.URI

interface AssetContainerFactory {
    suspend fun fromSource(source: ExternalContentReference): AssetDataContainer
}

sealed interface ExternalContentReference {
    data class Url(
        val url: URI,
    ) : ExternalContentReference

    data class S3Object(
        val bucket: String,
        val key: String,
    ) : ExternalContentReference
}

class InvalidAssetSourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AssetSourceForbiddenException(
    message: String,
) : RuntimeException(message)

class RemoteAssetTooLargeException : RuntimeException()

class AssetSourceUnavailableException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AssetSourceTimeoutException(
    cause: Throwable? = null,
) : RuntimeException(cause)
