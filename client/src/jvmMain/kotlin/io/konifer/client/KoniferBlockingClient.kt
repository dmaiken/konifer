package io.konifer.client

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

class KoniferBlockingClient internal constructor(
    private val client: KoniferClient,
) {
    companion object {
        /**
         * Synchronously builds a KoniferBlockingClient.
         */
        @JvmStatic
        @JvmOverloads
        fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
        ): KoniferBlockingClient =
            runBlocking {
                val asyncClient =
                    KoniferClient.build(
                        baseUrl = baseUrl,
                        hmacKey = hmacKey,
                        hmacSigningAlgorithm = hmacSigningAlgorithm,
                    )
                KoniferBlockingClient(asyncClient)
            }
    }

    fun fetchAssetMetadata(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
    ): KoniferResponse<AssetResponse> =
        runBlocking {
            client.fetchAssetMetadata(
                path = path,
                querySelectors = querySelectors,
                labels = labels,
            )
        }

    fun fetchAssetMetadata(
        path: String,
        limit: Int,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
    ): KoniferResponse<List<AssetResponse>> =
        runBlocking {
            client.fetchAssetMetadata(
                path = path,
                limit = limit,
                querySelectors = querySelectors,
                labels = labels,
            )
        }

    fun fetchAssetContent(
        path: String,
        outputStream: OutputStream,
        options: AssetContentRequestOptions,
    ): KoniferResponse<Unit> =
        runBlocking {
            val byteChannel = ByteChannel()
            val copyJob =
                async {
                    byteChannel.copyTo(outputStream)
                }
            val response =
                client.fetchAssetContent(
                    path = path,
                    querySelectors = options.querySelectors,
                    requestedTransformation = options.requestedTransformation,
                    fetchMode = options.fetchMode,
                    labels = options.labels,
                    byteChannel = byteChannel,
                )
            if (response is KoniferResponse.Success) {
                copyJob.await()
            } else {
                copyJob.cancel()
            }
            byteChannel.close()
            response
        }

    fun fetchAssetRedirectLocation(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<String> =
        runBlocking {
            client.fetchAssetRedirectLocation(
                path = path,
                querySelectors = querySelectors,
                labels = labels,
                requestedTransformation = requestedTransformation,
            )
        }

    fun fetchAssetContentBytes(
        path: String,
        options: AssetContentRequestOptions,
    ): KoniferResponse<ByteArray> =
        runBlocking {
            client.fetchAssetContentBytes(
                path = path,
                querySelectors = options.querySelectors,
                requestedTransformation = options.requestedTransformation,
                fetchMode = options.fetchMode,
                labels = options.labels,
            )
        }

    fun fetchAssetLink(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<AssetLinkResponse> =
        runBlocking {
            client.fetchAssetLink(
                path = path,
                querySelectors = querySelectors,
                requestedTransformation = requestedTransformation,
                labels = labels,
            )
        }

    fun storeAsset(
        path: String,
        format: ImageFormat,
        request: StoreAssetRequest,
        inputStream: InputStream,
    ): KoniferResponse<AssetResponse> =
        runBlocking {
            client.storeAsset(
                path = path,
                format = format,
                request = request,
                channel = inputStream.toByteReadChannel(),
            )
        }

    fun storeAsset(
        path: String,
        request: StoreAssetRequest,
    ): KoniferResponse<AssetResponse> =
        runBlocking {
            client.storeAsset(
                path = path,
                request = request,
            )
        }

    fun storeAsset(
        path: String,
        format: ImageFormat,
        request: StoreAssetRequest,
        bytes: ByteArray,
    ): KoniferResponse<AssetResponse> =
        storeAsset(
            path = path,
            format = format,
            request = request,
            inputStream = ByteArrayInputStream(bytes),
        )

    fun updateAsset(
        path: String,
        entryId: Long,
        request: StoreAssetRequest,
    ): KoniferResponse<AssetResponse> =
        runBlocking {
            client.updateAsset(
                path = path,
                entryId = entryId,
                request = request,
            )
        }

    fun deleteAsset(
        path: String,
        querySelectors: DeleteQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        limit: Int = 1,
    ): KoniferResponse<Unit> =
        runBlocking {
            client.deleteAsset(
                path = path,
                querySelectors = querySelectors,
                labels = labels,
                limit = limit,
            )
        }

    fun close() {
        client.close()
    }
}
