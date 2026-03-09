package io.konifer.client

import io.konifer.common.http.AssetResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClient(
    private val baseUrl: String,
) {
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


    suspend fun getAssetMetadata(path: String): AssetResponse {
        val stripped = path.removePrefix("/").removeSuffix("/")
        val response: HttpResponse = httpClient.get("/assets/$stripped")
        return response.body()
    }

    suspend fun getAssetMetadata(path: String, limit: Int): List<AssetResponse> {
        TODO()
    }

    suspend fun getAssetContent(path: String): ByteArray {
        val stripped = path.removePrefix("/").removeSuffix("/")
        val response: HttpResponse = httpClient.get("/assets/$stripped")
        return response.bodyAsBytes()
    }

    fun close() {
        httpClient.close()
    }
}
