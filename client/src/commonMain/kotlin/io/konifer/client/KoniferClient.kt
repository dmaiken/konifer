package io.konifer.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
                        prettyPrint = true
                    },
                )
            }
            defaultRequest {
                url(baseUrl)
            }
        }

    // Example: Fetching Asset Metadata
    suspend fun getAssetMetadata(assetId: String): String {
        val response: HttpResponse = httpClient.get("/api/assets/$assetId")
        return response.bodyAsText() // Swap with a kotlinx @Serializable data class
    }

    // Example: Fetching Asset Bytes (since Konifer delegates to libvips)
    suspend fun downloadAssetBytes(assetId: String): ByteArray = httpClient.get("/api/assets/$assetId/content").body<ByteArray>()

    fun close() {
        httpClient.close()
    }
}
