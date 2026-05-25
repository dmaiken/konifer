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

class KoniferBlockingClient(
    private val client: KoniferClient,
) {
    fun getAssetMetadata(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<AssetResponse> =
        runBlocking {
            client.fetchAssetMetadata(
                path = path,
                querySelectors = querySelectors,
            )
        }

    fun getAssetMetadata(
        path: String,
        limit: Int,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<List<AssetResponse>> =
        runBlocking {
            client.fetchAssetMetadata(
                path = path,
                limit = limit,
                querySelectors = querySelectors,
            )
        }

    fun getAssetContent(
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

    fun getAssetRedirectLocation(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<String> =
        runBlocking {
            client.fetchAssetRedirectLocation(
                path = path,
                querySelectors = querySelectors,
                requestedTransformation = requestedTransformation,
            )
        }

    fun getAssetContentBytes(
        path: String,
        options: AssetContentRequestOptions,
    ): KoniferResponse<ByteArray> =
        runBlocking {
            client.fetchAssetContentBytes(
                path = path,
                querySelectors = options.querySelectors,
                requestedTransformation = options.requestedTransformation,
                fetchMode = options.fetchMode,
            )
        }

    fun getAssetLink(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<AssetLinkResponse> =
        runBlocking {
            client.fetchAssetLink(
                path = path,
                querySelectors = querySelectors,
                requestedTransformation = requestedTransformation,
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

    fun close() {
        client.close()
    }
}
