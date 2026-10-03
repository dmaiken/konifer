package io.konifer.client.assets.store

import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.flowUploadChannel
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Selects the image source for a new asset.
 *
 * Obtain this stage from [io.konifer.client.assets.AssetAtPath.newAsset]. After selecting a
 * source, add metadata and call [NewAssetAtPath.store].
 */
class BlankAssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
) {
    /**
     * Uses the image at [url] as the new asset's source.
     *
     * The Konifer server must allow the URL's domain through `source.url.allowed-domains`.
     */
    fun fromUrl(url: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetReferenceContentSource.UrlSource(url),
                ),
        )

    /** Uses the S3 object identified by [s3Arn] as the new asset's source. */
    fun fromS3Arn(s3Arn: String): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetReferenceContentSource.S3ArnSource(s3Arn),
                ),
        )

    /**
     * Uses a snapshot of [bytes] as the new asset's source.
     *
     * This method copies [bytes], so later caller mutations do not affect uploads.
     *
     * @param format image format advertised for the uploaded bytes.
     */
    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetByteContentSource.ByteArraySource(bytes, format),
                ),
        )

    /**
     * Streams the image from a supplied channel.
     *
     * The HTTP client may invoke [open] more than once. Each invocation must return a fresh,
     * readable channel containing the complete image.
     *
     * @param format image format advertised for the uploaded bytes.
     */
    fun fromChannel(
        open: () -> ByteReadChannel,
        format: ImageFormat,
    ): NewAssetAtPath = fromChannelSource({ open() }, format)

    /**
     * Streams the image from byte-array chunks.
     *
     * The HTTP client may invoke [chunks] and collect its result more than once. Each invocation
     * must supply a flow that supports an independent collection of the complete image.
     *
     * @param format image format advertised for the uploaded bytes.
     */
    fun fromChunks(
        chunks: () -> Flow<ByteArray>,
        format: ImageFormat,
    ): NewAssetAtPath = fromChannelSource({ scope -> flowUploadChannel(scope, chunks) }, format)

    private fun fromChannelSource(
        open: (CoroutineScope) -> ByteReadChannel,
        format: ImageFormat,
    ): NewAssetAtPath =
        NewAssetAtPath(
            infra = infra,
            path = path,
            requestBuilder =
                AssetRequestBuilder(
                    assetSource = AssetByteContentSource.ByteChannelSource(open, format),
                ),
        )
}
