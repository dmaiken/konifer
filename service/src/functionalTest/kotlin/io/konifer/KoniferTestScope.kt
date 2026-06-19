package io.konifer

import io.konifer.client.HmacSigningAlgorithm
import io.konifer.client.KoniferClient
import io.konifer.client.KoniferInternalTestApi
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

class KoniferTestScope(
    private val builder: ApplicationTestBuilder,
    coroutineScope: CoroutineScope,
) : CoroutineScope by coroutineScope {
    private var currentClient = createClient()
    private var hmacKey: String? = null
    private var hmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256

    val client: HttpClient get() = currentClient

    // Backing field to cache the initialized client
    private var cachedKoniferClient: KoniferClient? = null

    @OptIn(KoniferInternalTestApi::class)
    suspend fun konifer(): KoniferClient =
        cachedKoniferClient ?: KoniferClient
            .buildForTesting(
                testClient = client,
                hmacKey = hmacKey,
                hmacSigningAlgorithm = hmacSigningAlgorithm,
            ).also { cachedKoniferClient = it }

    fun configureKoniferHmacSigning(
        hmacKey: String?,
        hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
    ) {
        this.hmacKey = hmacKey
        this.hmacSigningAlgorithm = hmacSigningAlgorithm
        cachedKoniferClient = null
    }

    /**
     * Configure the test [HttpClient]. JSON content negotiation is already enabled.
     */
    fun configureClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
        currentClient.close()
        currentClient = createClient(block)
        return currentClient
    }

    private fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}) =
        builder.createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        explicitNulls = false
                    },
                )
            }
            block()
        }
}
