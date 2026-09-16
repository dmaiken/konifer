package io.konifer.entrypoint

import io.konifer.domain.asset.AssetDataContainer
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.RoutingCall
import kotlinx.coroutines.CompletableDeferred

private const val METADATA_PART_NAME = "metadata"
private const val ASSET_PART_NAME = "asset"

internal class MultipartUpload<T>(
    val request: CompletableDeferred<T>,
    val assetContainer: AssetDataContainer?,
    val duplicateAssetReceived: Boolean,
    val duplicateMetadataReceived: Boolean,
) : AutoCloseable {
    override fun close() {
        assetContainer?.close()
    }
}

internal suspend fun <T> RoutingCall.receiveMultipartUpload(decodeMetadata: (String) -> T): MultipartUpload<T> {
    val request = CompletableDeferred<T>()
    var assetPartReceived = false
    var metadataPartReceived = false
    var assetContainer: AssetDataContainer? = null
    var duplicateAssetReceived = false
    var duplicateMetadataReceived = false

    try {
        receiveMultipart().forEachPart { part ->
            when (part.name) {
                METADATA_PART_NAME -> {
                    if (metadataPartReceived) {
                        duplicateMetadataReceived = true
                        part.release()
                    } else {
                        metadataPartReceived = true
                        part.readMetadataInto(request, decodeMetadata)
                    }
                }
                ASSET_PART_NAME -> {
                    if (assetPartReceived) {
                        duplicateAssetReceived = true
                        part.release()
                    } else {
                        assetPartReceived = true
                        assetContainer = part.copyAssetContentToTemporaryFile()
                    }
                }
                else -> part.release()
            }
        }
    } catch (e: Throwable) {
        assetContainer?.close()
        throw e
    }

    return MultipartUpload(
        request = request,
        assetContainer = assetContainer,
        duplicateAssetReceived = duplicateAssetReceived,
        duplicateMetadataReceived = duplicateMetadataReceived,
    )
}

private suspend fun <T> PartData.readMetadataInto(
    request: CompletableDeferred<T>,
    decodeMetadata: (String) -> T,
) {
    try {
        if (this is PartData.FormItem) {
            request.complete(decodeMetadata(value))
        }
    } finally {
        release()
    }
}
