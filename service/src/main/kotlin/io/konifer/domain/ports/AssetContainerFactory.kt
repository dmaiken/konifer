package io.konifer.domain.ports

import io.konifer.domain.asset.AssetDataContainer

interface AssetContainerFactory {
    suspend fun fromUrlSource(urlSource: String?): AssetDataContainer
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
