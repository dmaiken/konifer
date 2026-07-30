package io.konifer.client

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.cancel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.json.Json

class KoniferClient internal constructor(
    private val httpClient: HttpClient,
    private val urlSigner: KoniferUrlSigner? = null,
) {
    companion object {
        private const val ASSETS_BASE_PATH = "assets"
        private const val RULE_EVALUATIONS_PATH = "/rule-evaluations"
        private const val BOUNDARY = "boundary"
        private const val ASSET_FORM_KEY = "asset"
        private const val METADATA_FORM_KEY = "metadata"

        suspend fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
        ): KoniferClient {
            val httpClient =
                HttpClient {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                explicitNulls = false
                            },
                        )
                    }
                    defaultRequest {
                        url(baseUrl)
                    }
                }
            return KoniferClient(
                httpClient = httpClient,
                urlSigner =
                    hmacKey?.let {
                        KoniferUrlSigner.create(
                            HmacSigningConfig(
                                secretKey = it,
                                algorithm = hmacSigningAlgorithm,
                            ),
                        )
                    },
            )
        }

        @KoniferInternalTestApi
        suspend fun buildForTesting(
            testClient: HttpClient,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
        ): KoniferClient =
            KoniferClient(
                httpClient = testClient,
                urlSigner =
                    hmacKey?.let {
                        KoniferUrlSigner.create(
                            HmacSigningConfig(
                                secretKey = it,
                                algorithm = hmacSigningAlgorithm,
                            ),
                        )
                    },
            )
    }

    private val noRedirectClient =
        httpClient.config {
            followRedirects = false
        }

    suspend fun fetchAssetInfo(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
    ): KoniferResponse<AssetResponse> =
        safeApiCall {
            val requestUrl =
                signedUrl {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.INFO, querySelectors)
                    appendLabels(labels)
                }
            httpClient
                .get {
                    url.takeFrom(requestUrl)
                    accept(ContentType.Application.Json)
                }.toKoniferResponse()
        }

    suspend fun fetchAssetInfo(
        path: String,
        limit: Int,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
    ): KoniferResponse<List<AssetResponse>> =
        safeApiCall {
            val requestUrl =
                signedUrl {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.INFO, querySelectors)
                    appendLimit(limit)
                    appendLabels(labels)
                }
            httpClient
                .get {
                    url.takeFrom(requestUrl)
                    accept(ContentType.Application.Json)
                }.toKoniferResponse()
        }

    suspend fun fetchAssetContent(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
        byteChannel: ByteChannel,
        fetchMode: ContentFetchMode = ContentFetchMode.CONTENT,
    ): KoniferResponse<Unit> =
        safeApiCall {
            val requestUrl =
                fetchContentUrl(
                    path = path,
                    querySelectors = querySelectors,
                    labels = labels,
                    requestedTransformation = requestedTransformation,
                    fetchMode = fetchMode,
                )
            httpClient
                .prepareGet {
                    url.takeFrom(requestUrl)
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

    suspend fun fetchAssetContentBytes(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
        fetchMode: ContentFetchMode = ContentFetchMode.CONTENT,
    ): KoniferResponse<ByteArray> =
        safeApiCall {
            val requestUrl =
                fetchContentUrl(
                    path = path,
                    querySelectors = querySelectors,
                    labels = labels,
                    requestedTransformation = requestedTransformation,
                    fetchMode = fetchMode,
                )
            httpClient
                .prepareGet {
                    url.takeFrom(requestUrl)
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        KoniferResponse.Success(response.bodyAsBytes())
                    } else {
                        response.toKoniferResponse()
                    }
                }
        }

    suspend fun fetchAssetRedirectLocation(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<String> =
        safeApiCall {
            val requestUrl =
                signedUrl {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.REDIRECT, querySelectors)
                    appendTransformationParameters(requestedTransformation)
                    appendLabels(labels)
                }
            noRedirectClient
                .prepareGet {
                    url.takeFrom(requestUrl)
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

    suspend fun fetchAssetLink(
        path: String,
        querySelectors: FetchQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<AssetLinkResponse> =
        safeApiCall {
            val requestUrl =
                signedUrl {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.LINK, querySelectors)
                    appendTransformationParameters(requestedTransformation)
                    appendLabels(labels)
                }
            httpClient
                .get {
                    url.takeFrom(requestUrl)
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
                    setBody(assetUploadFormData(request, format, channel))
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

    suspend fun updateAsset(
        path: String,
        entryId: Long,
        request: StoreAssetRequest,
    ): KoniferResponse<AssetResponse> =
        safeApiCall {
            httpClient
                .put {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(
                            returnFormat = null,
                            querySelectors = EntryId(entryId),
                        )
                    }
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.toKoniferResponse()
        }

    suspend fun deleteAsset(
        path: String,
        querySelectors: DeleteQuerySelector = None(),
        labels: Map<String, String> = emptyMap(),
        limit: Int = 1,
    ): KoniferResponse<Unit> =
        safeApiCall {
            httpClient
                .delete {
                    url {
                        appendPathSegments(ASSETS_BASE_PATH)
                        appendPathSegments(path.splitPath())
                        appendQuerySelectors(
                            returnFormat = null,
                            querySelectors = querySelectors,
                        )
                        appendLabels(labels)
                        appendLimit(limit)
                    }
                }.toKoniferResponse()
        }

    /**
     * Evaluate rules against content provided by the URL within the [request].
     */
    suspend fun evaluateRules(request: EvaluateRuleDefinitionsRequest): KoniferResponse<EvaluateRuleDefinitionsResponse> {
        if (request.url.isNullOrBlank()) {
            throw IllegalArgumentException("URL is required in request")
        }

        return safeApiCall {
            httpClient
                .post {
                    url {
                        appendPathSegments(RULE_EVALUATIONS_PATH)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.toKoniferResponse()
        }
    }

    suspend fun evaluateRules(
        request: EvaluateRuleDefinitionsRequest,
        format: ImageFormat,
        channel: ByteReadChannel,
    ): KoniferResponse<EvaluateRuleDefinitionsResponse> {
        if (request.url?.isNotBlank() == true) {
            throw IllegalArgumentException("URL cannot be supplied when content is also supplied")
        }
        return safeApiCall {
            httpClient
                .post {
                    url {
                        appendPathSegments(RULE_EVALUATIONS_PATH)
                    }
                    contentType(ContentType.MultiPart.FormData)
                    setBody(assetUploadFormData(request, format, channel))
                }.toKoniferResponse()
        }
    }

    suspend fun evaluateRules(
        format: ImageFormat,
        request: EvaluateRuleDefinitionsRequest,
        bytes: ByteArray,
    ): KoniferResponse<EvaluateRuleDefinitionsResponse> =
        evaluateRules(
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

    private inline fun <reified T> assetUploadFormData(
        request: T,
        format: ImageFormat,
        channel: ByteReadChannel,
    ): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                append(
                    key = METADATA_FORM_KEY,
                    value = Json.encodeToString(request),
                    headers =
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        },
                )
                append(
                    key = ASSET_FORM_KEY,
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
        )

    private suspend fun fetchContentUrl(
        path: String,
        querySelectors: FetchQuerySelector,
        labels: Map<String, String>,
        requestedTransformation: RequestedTransformation,
        fetchMode: ContentFetchMode,
    ): URLBuilder =
        signedUrl {
            appendPathSegments(ASSETS_BASE_PATH)
            appendPathSegments(path.splitPath())
            when (fetchMode) {
                ContentFetchMode.CONTENT -> appendQuerySelectors(ReturnFormat.CONTENT, querySelectors)
                ContentFetchMode.REDIRECT -> appendQuerySelectors(ReturnFormat.REDIRECT, querySelectors)
            }
            appendTransformationParameters(requestedTransformation)
            appendLabels(labels)
        }

    private suspend fun signedUrl(block: URLBuilder.() -> Unit): URLBuilder =
        URLBuilder()
            .apply(block)
            .apply {
                urlSigner?.let { signer ->
                    parameters.append(signer.signatureParameter, signer.sign(this))
                }
            }
}
