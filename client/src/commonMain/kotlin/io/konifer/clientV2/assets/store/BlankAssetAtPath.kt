package io.konifer.clientV2.assets.store

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel

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

    fun fromChannel(
        channel: ByteReadChannel,
        format: ImageFormat,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetByteContentSource.AssetByteChannelContentSource(channel, format),
                ),
        )
}
