package io.konifer.client

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
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

    @JvmOverloads
    suspend fun getAssetMetadata(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<AssetResponse> {
        val response: HttpResponse =
            httpClient.get {
                url {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.METADATA, querySelectors)
                }
                accept(ContentType.Application.Json)
            }
        return response.toKoniferResponse()
    }

    @JvmOverloads
    suspend fun getAssetMetadata(
        path: String,
        limit: Int,
        querySelectors: QuerySelectors = QuerySelectors.None(),
    ): KoniferResponse<List<AssetResponse>> {
        val response: HttpResponse =
            httpClient.get {
                url {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.METADATA, querySelectors)
                    parameters.append(LIMIT_PARAMETER, limit.toString())
                }
                accept(ContentType.Application.Json)
            }
        return response.toKoniferResponse()
    }

    suspend fun getAssetContent(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
        byteChannel: ByteChannel,
    ): KoniferResponse<Unit> {
        return httpClient.prepareGet {
                url {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.CONTENT, querySelectors)
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

    suspend fun getAssetLink(
        path: String,
        querySelectors: QuerySelectors = QuerySelectors.None(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferResponse<AssetLinkResponse> {
        return httpClient.get {
            url {
                appendPathSegments(ASSETS_BASE_PATH)
                appendPathSegments(path.splitPath())
                appendQuerySelectors(ReturnFormat.LINK, querySelectors)
                appendTransformationParameters(requestedTransformation)
            }
            accept(ContentType.Application.Json)
        }.toKoniferResponse()
    }

    fun close() {
        httpClient.close()
    }

    private fun String.splitPath() = this.removePrefix("/").removeSuffix("/").split("/")
}
