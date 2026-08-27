package io.konifer.infrastructure.asset

import io.konifer.domain.ByteSize
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.domain.asset.AssetDataTooLargeException
import io.konifer.domain.ports.AssetContainerFactory
import io.konifer.domain.ports.AssetSourceForbiddenException
import io.konifer.domain.ports.AssetSourceTimeoutException
import io.konifer.domain.ports.AssetSourceUnavailableException
import io.konifer.domain.ports.InvalidAssetSourceException
import io.konifer.domain.ports.RemoteAssetTooLargeException
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class UrlAssetStreamContainerFactory(
    allowedDomains: Set<String>,
    private val maxBytes: ByteSize,
    private val httpClient: HttpClient,
) : AssetContainerFactory {
    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val SUPPORTED_SCHEMES = setOf("http", "https")
    }

    private val normalizedAllowedDomains = allowedDomains.mapTo(mutableSetOf()) { it.lowercase() }

    override suspend fun fromUrlSource(urlSource: String?): AssetDataContainer {
        if (urlSource == null) {
            throw InvalidAssetSourceException("URL source must be supplied")
        }
        val uri =
            try {
                URI.create(urlSource).normalize()
            } catch (cause: Exception) {
                throw InvalidAssetSourceException("$urlSource is not a valid URL", cause)
            }

        validateSourceUri(uri)

        return translateRemoteFailure {
            fetchFollowingRedirects(uri)
        }
    }

    private suspend fun fetchFollowingRedirects(initialUri: URI): AssetDataContainer {
        val visited = mutableSetOf<URI>()
        var currentUri = initialUri
        var redirectsFollowed = 0

        while (true) {
            validateSourceUri(currentUri)
            if (!visited.add(currentUri)) {
                throw InvalidAssetSourceException("Asset source redirect loop detected")
            }

            when (val result = fetchOnce(currentUri)) {
                is FetchResult.Asset -> return result.container
                is FetchResult.Redirect -> {
                    if (redirectsFollowed >= MAX_REDIRECTS) {
                        throw InvalidAssetSourceException(
                            "Asset source exceeded the maximum of $MAX_REDIRECTS redirects",
                        )
                    }
                    redirectsFollowed++
                    currentUri = result.target
                }
            }
        }
    }

    private suspend fun fetchOnce(uri: URI): FetchResult =
        httpClient.prepareGet(uri.toURL()).execute { response ->
            if (response.status.value in REDIRECT_STATUS_CODES) {
                return@execute FetchResult.Redirect(resolveRedirect(uri, response))
            }

            validateRemoteStatus(response)
            validateContentLength(response)

            val container = AssetDataContainer(response.bodyAsChannel(), maxBytes.bytes)
            try {
                // Streaming responses are only valid inside execute, so materialize the bounded body before returning.
                container.toTemporaryFile("")
                FetchResult.Asset(container)
            } catch (_: AssetDataTooLargeException) {
                container.close()
                throw RemoteAssetTooLargeException()
            } catch (cause: Throwable) {
                container.close()
                throw cause
            }
        }

    private fun resolveRedirect(
        currentUri: URI,
        response: HttpResponse,
    ): URI {
        val location =
            response.headers[HttpHeaders.Location]
                ?: throw InvalidAssetSourceException(
                    "Asset source returned redirect ${response.status.value} without a Location header",
                )

        val target =
            try {
                currentUri.resolve(location).normalize()
            } catch (cause: Exception) {
                throw InvalidAssetSourceException("Asset source returned an invalid redirect URL", cause)
            }

        validateSourceUri(target)
        if (currentUri.scheme.equals("https", ignoreCase = true) && target.scheme.equals("http", ignoreCase = true)) {
            throw InvalidAssetSourceException("Asset source cannot redirect from HTTPS to HTTP")
        }
        return target
    }

    private fun validateSourceUri(uri: URI) {
        val scheme = uri.scheme?.lowercase()
        if (scheme !in SUPPORTED_SCHEMES) {
            throw InvalidAssetSourceException("Asset source must use HTTP or HTTPS")
        }

        val host =
            uri.host?.lowercase()
                ?: throw InvalidAssetSourceException("Asset source must include a valid host")
        if (host !in normalizedAllowedDomains) {
            throw AssetSourceForbiddenException("Not permitted host domain: $host")
        }
    }

    private fun validateContentLength(response: HttpResponse) {
        val value = response.headers[HttpHeaders.ContentLength] ?: return
        val contentLength =
            value.toLongOrNull()
                ?: throw AssetSourceUnavailableException("Asset source returned an invalid Content-Length header")
        if (contentLength > maxBytes.bytes) {
            throw RemoteAssetTooLargeException()
        }
    }

    private suspend fun <T> translateRemoteFailure(block: suspend () -> T): T =
        try {
            block()
        } catch (cause: HttpRequestTimeoutException) {
            throw AssetSourceTimeoutException(cause)
        } catch (cause: ConnectTimeoutException) {
            throw AssetSourceTimeoutException(cause)
        } catch (cause: SocketTimeoutException) {
            throw AssetSourceTimeoutException(cause)
        } catch (cause: UnknownHostException) {
            throw AssetSourceUnavailableException("Asset source host could not be resolved", cause)
        } catch (cause: SSLException) {
            throw AssetSourceUnavailableException("TLS connection to asset source failed", cause)
        } catch (cause: ConnectException) {
            throw AssetSourceUnavailableException("Could not connect to asset source", cause)
        } catch (cause: IOException) {
            throw AssetSourceUnavailableException("Failed to retrieve asset source", cause)
        }

    private fun validateRemoteStatus(response: HttpResponse) {
        when (response.status.value) {
            in 300..399 ->
                throw InvalidAssetSourceException(
                    "Asset source returned unsupported redirect ${response.status.value}",
                )

            in 400..499 ->
                throw InvalidAssetSourceException(
                    "Asset source returned ${response.status.value}",
                )

            in 500..599 ->
                throw AssetSourceUnavailableException(
                    "Asset source returned ${response.status.value}",
                )
        }
    }

    private sealed interface FetchResult {
        data class Redirect(
            val target: URI,
        ) : FetchResult

        data class Asset(
            val container: AssetDataContainer,
        ) : FetchResult
    }
}
