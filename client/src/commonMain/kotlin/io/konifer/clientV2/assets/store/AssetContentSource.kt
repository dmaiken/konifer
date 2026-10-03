package io.konifer.clientV2.assets.store

import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope

internal sealed interface AssetContentSource

internal sealed interface AssetReferenceContentSource : AssetContentSource {
    fun applyToRequest(request: StoreAssetRequest): StoreAssetRequest

    data class UrlSource(
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

    data class S3ArnSource(
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

internal sealed interface AssetByteContentSource : AssetContentSource {
    val format: ImageFormat

    fun channel(scope: CoroutineScope): ByteReadChannel

    class ByteArraySource(
        bytes: ByteArray,
        override val format: ImageFormat,
    ) : AssetByteContentSource {
        private val bytes = bytes.copyOf()

        override fun channel(scope: CoroutineScope): ByteReadChannel = ByteReadChannel(bytes)
    }

    class ByteChannelSource(
        private val open: (CoroutineScope) -> ByteReadChannel,
        override val format: ImageFormat,
    ) : AssetByteContentSource {
        override fun channel(scope: CoroutineScope): ByteReadChannel = open(scope)
    }
}
