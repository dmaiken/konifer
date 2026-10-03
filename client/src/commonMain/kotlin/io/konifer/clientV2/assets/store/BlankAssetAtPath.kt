package io.konifer.clientV2.assets.store

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.internal.flowUploadChannel
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class BlankAssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
) {
    fun fromUrl(url: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetReferenceContentSource.AssetUrlContentSource(url),
                ),
        )

    fun fromS3Arn(s3Arn: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetReferenceContentSource.AssetS3ArnContentSource(s3Arn),
                ),
        )

    /** Copies [bytes] so subsequent changes to the array do not affect uploads. */
    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetByteContentSource.AssetByteArrayContentSource(bytes, format),
                ),
        )

    /** [open] must return a fresh channel whenever an upload is sent or replayed. */
    fun fromChannel(
        open: () -> ByteReadChannel,
        format: ImageFormat,
    ): NewAssetAtPath = fromChannelSource({ open() }, format)

    /** [chunks] must provide a new, collectable flow for each upload attempt. */
    fun fromChunks(
        chunks: () -> Flow<ByteArray>,
        format: ImageFormat,
    ): NewAssetAtPath = fromChannelSource({ scope -> flowUploadChannel(scope, chunks) }, format)

    internal fun fromChannelSource(
        open: (CoroutineScope) -> ByteReadChannel,
        format: ImageFormat,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetByteContentSource.AssetByteChannelContentSource(open, format),
                ),
        )
}
