package io.konifer.domain.ports

import io.konifer.common.http.AssetSource
import io.konifer.domain.asset.AssetDataContainer

interface AssetContainerFactory {
    suspend fun fromSource(source: AssetSource): AssetDataContainer
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
