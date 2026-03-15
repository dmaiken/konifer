package io.konifer.client

import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.ReturnFormat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
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
        byteChannel: ByteChannel,
    ): KoniferResponse<Unit> {
        val response: HttpResponse =
            httpClient.get {
                url {
                    appendPathSegments(ASSETS_BASE_PATH)
                    appendPathSegments(path.splitPath())
                    appendQuerySelectors(ReturnFormat.CONTENT, querySelectors)
                }
            }

        return if (response.status.isSuccess()) {
            response.bodyAsChannel().copyAndClose(byteChannel)
            KoniferResponse.Success(Unit)
        } else {
            response.toKoniferResponse()
        }
    }

    fun close() {
        httpClient.close()
    }

    private fun String.splitPath() = this.removePrefix("/").removeSuffix("/").split("/")
}
