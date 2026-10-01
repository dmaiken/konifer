package io.konifer.clientV2.assets.store

import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel

sealed interface AssetContentSource

interface AssetReferenceContentSource : AssetContentSource {
    fun applyToRequest(request: StoreAssetRequest): StoreAssetRequest

    data class AssetUrlContentSource(
        private val url: String,
    ) : AssetReferenceContentSource {
        override fun applyToRequest(request: StoreAssetRequest): StoreAssetRequest =
            request.copy(
                source =
                    AssetSourceRequest(
                        http =
                            HttpSource(
                                url = url,
                            ),
                    ),
            )
    }

    data class AssetS3ArnContentSource(
        private val s3Arn: String,
    ) : AssetReferenceContentSource {
        override fun applyToRequest(request: StoreAssetRequest): StoreAssetRequest =
            request.copy(
                source =
                    AssetSourceRequest(
                        s3 =
                            S3Source(
                                arn = s3Arn,
                            ),
                    ),
            )
    }
}

interface AssetByteContentSource : AssetContentSource {
    val format: ImageFormat

    fun channel(): ByteReadChannel

    data class AssetByteArrayContentSource(
        private val bytes: ByteArray,
        override val format: ImageFormat,
    ) : AssetByteContentSource {
        override fun channel(): ByteReadChannel = ByteReadChannel(bytes)
    }

    data class AssetByteChannelContentSource(
        private val channel: ByteReadChannel,
        override val format: ImageFormat,
    ) : AssetByteContentSource {
        override fun channel(): ByteReadChannel = channel
    }
}
