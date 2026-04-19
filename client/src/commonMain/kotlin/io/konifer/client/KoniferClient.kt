package io.konifer.client

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.cancel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmOverloads

class KoniferClient internal constructor(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val ASSETS_BASE_PATH = "assets"
        private const val LIMIT_PARAMETER = "limit"
        private const val BOUNDARY = "boundary"

        fun build(baseUrl: String): KoniferClient {
            val httpClient =
                HttpClient {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                            },
                        )
                    }
                    defaultRequest {
                        url(baseUrl)
                    }
                }
            return KoniferClient(httpClient)
        }
    }

    private val noRedirectClient =
        httpClient.config {
            followRedirects = false
        }

    @JvmOverloads
    suspend fun getAssetMetadata(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<AssetResponse> =
        safeApiCall {
            httpClient
                .get {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(ReturnFormat.METADATA, querySelectors)
                    }
                    accept(ContentType.Application.Json)
                }.toKoniferResponse()
        }

    @JvmOverloads
    suspend fun getAssetMetadata(
        path: String,
        limit: Int,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<List<AssetResponse>> =
        safeApiCall {
            httpClient
                .get {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(ReturnFormat.METADATA, querySelectors)
                        parameters.append(LIMIT_PARAMETER, limit.toString())
                    }
                    accept(ContentType.Application.Json)
                }.toKoniferResponse()
        }

    suspend fun getAssetContent(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
        byteChannel: ByteChannel,
        requestRedirect: Boolean,
    ): KoniferResponse<Unit> =
        safeApiCall {
            httpClient
                .prepareGet {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        if (requestRedirect) {
                            appendQuerySelectors(ReturnFormat.REDIRECT, querySelectors)
                        } else {
                            appendQuerySelectors(ReturnFormat.CONTENT, querySelectors)
                        }
                        appendTransformationParameters(requestedTransformation)
                    }
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        response.bodyAsChannel().copyAndClose(byteChannel)
                        KoniferResponse.Success(Unit)
                    } else {
                        byteChannel.cancel()
                        response.toKoniferResponse()
                    }
                }
        }

    suspend fun getAssetRedirectLocation(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<String> =
        safeApiCall {
            noRedirectClient
                .prepareGet {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(ReturnFormat.REDIRECT, querySelectors)
                        appendTransformationParameters(requestedTransformation)
                    }
                }.execute { response ->
                    if (response.status.value in 300..399) {
                        val locationUrl =
                            response.headers[HttpHeaders.Location]
                                ?: throw IllegalStateException("Server returned a redirect status but no Location header")

                        KoniferResponse.Success(locationUrl)
                    } else {
                        response.toKoniferResponse()
                    }
                }
        }

    suspend fun getAssetLink(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<AssetLinkResponse> =
        safeApiCall {
            httpClient
                .get {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(ReturnFormat.LINK, querySelectors)
                        appendTransformationParameters(requestedTransformation)
                    }
                    accept(ContentType.Application.Json)
                }.toKoniferResponse()
        }

    /**
     * Store an asset by providing the asset content.
     */
    suspend fun storeAsset(
        path: String,
        format: ImageFormat,
        request: StoreAssetRequest,
        channel: ByteReadChannel,
    ): KoniferResponse<AssetResponse> {
        if (request.url?.isNotBlank() == true) {
            throw IllegalArgumentException("URL cannot be supplied when asset content is also supplied")
        }
        return safeApiCall {
            httpClient
                .post {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                    }
                    contentType(ContentType.MultiPart.FormData)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    key = "metadata",
                                    value = Json.encodeToString(request),
                                    headers =
                                        Headers.build {
                                            append(HttpHeaders.ContentType, "application/json")
                                        },
                                )
                                append(
                                    key = "asset",
                                    value = ChannelProvider { channel },
                                    headers =
                                        Headers.build {
                                            append(HttpHeaders.ContentType, format.mimeType)
                                            append(HttpHeaders.ContentDisposition, "filename=\"upload.bin\"")
                                        },
                                )
                            },
                            BOUNDARY,
                            ContentType.MultiPart.FormData.withParameter("boundary", BOUNDARY),
                        ),
                    )
                }.toKoniferResponse()
        }
    }

    /**
     * Store an asset by providing the URL to the asset within the [request].
     */
    suspend fun storeAsset(
        path: String,
        request: StoreAssetRequest,
    ): KoniferResponse<AssetResponse> {
        if (request.url.isNullOrBlank()) {
            throw IllegalArgumentException("URL is required in request")
        }
        return safeApiCall {
            httpClient
                .post {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                    }
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.toKoniferResponse()
        }
    }

    suspend fun storeAsset(
        path: String,
        format: ImageFormat,
        request: StoreAssetRequest,
        bytes: ByteArray,
    ): KoniferResponse<AssetResponse> =
        storeAsset(
            path = path,
            format = format,
            request = request,
            channel = ByteReadChannel(bytes),
        )

    fun close() {
        httpClient.close()
        noRedirectClient.close()
    }

    private fun String.splitPath() = this.removePrefix("/").removeSuffix("/").split("/")

    private inline fun <T> safeApiCall(apiCall: () -> KoniferResponse<T>): KoniferResponse<T> =
        try {
            apiCall()
        } catch (e: CancellationException) {
            // Always re-throw cancellation exceptions so coroutines can cancel!
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            KoniferResponse.NetworkError(e)
        }
}
