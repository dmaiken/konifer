package io.direkt.infrastructure.asset

import io.direkt.domain.asset.AssetDataContainer
import io.direkt.domain.ports.AssetContainerFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import java.net.URI

class AssetStreamContainerFactory(
    private val allowedDomains: Set<String>,
    private val maxBytes: Long,
    private val httpClient: HttpClient,
) : AssetContainerFactory {
    companion object {
        private const val CONTENT_LENGTH_HEADER = "Content-Length"
    }

    override suspend fun fromUrlSource(urlSource: String?): AssetDataContainer {
        if (urlSource == null) {
            throw IllegalArgumentException("URL source must be supplied")
        }
        val url =
            try {
                URI.create(urlSource).toURL()
            } catch (e: Exception) {
                throw IllegalArgumentException("$urlSource is not a valid URL", e)
            }

        if (!allowedDomains.contains(url.host)) {
            throw IllegalArgumentException("Not permitted host domain: ${url.host}")
        }

        val response = httpClient.get(url)
        if ((response.headers[CONTENT_LENGTH_HEADER]?.toLong() ?: 0L) > maxBytes) {
            throw IllegalArgumentException("Asset from URL source is too large")
        }

        return AssetDataContainer(response.bodyAsChannel())
    }
}
