package io.konifer.client

import io.konifer.client.assets.AssetAtPath
import io.konifer.client.internal.HmacSigningConfig
import io.konifer.client.internal.KoniferUrlSigner
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.rules.BlankRuleEvaluation
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Coroutine-based client for storing, selecting, transforming, and evaluating images in Konifer.
 *
 * Create an instance with [build] and call [close] when the client is no longer needed.
 */
class KoniferClient internal constructor(
    private val httpClient: HttpClient,
    private val urlSigner: KoniferUrlSigner? = null,
) {
    companion object {
        /**
         * Creates a client with Konifer's serialization, signing, and HTTP configuration.
         *
         * An omitted [engine] is created and owned by this client. A supplied engine (for example,
         * Ktor's MockEngine for customer tests) remains caller-owned and must be closed separately
         * after all clients using it have been closed. No preconfigured HttpClient is required.
         *
         * @param baseUrl base URL of the Konifer server.
         * @param hmacKey shared secret used to sign asset retrieval URLs, or `null` to disable signing.
         * @param hmacSigningAlgorithm digest algorithm used when [hmacKey] is present.
         * @param httpConfiguration request timeout configuration.
         * @param engine optional caller-owned Ktor engine.
         * @throws IllegalArgumentException if [hmacKey] is empty.
         */
        suspend fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
            httpConfiguration: KoniferHttpConfiguration = KoniferHttpConfiguration.Default,
            engine: HttpClientEngine? = null,
        ): KoniferClient {
            val urlSigner =
                hmacKey?.let {
                    KoniferUrlSigner.create(
                        HmacSigningConfig(
                            secretKey = it,
                            algorithm = hmacSigningAlgorithm,
                        ),
                    )
                }
            val configure: HttpClientConfig<*>.() -> Unit = {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = httpConfiguration.requestTimeout?.inWholeMilliseconds
                    connectTimeoutMillis = httpConfiguration.connectTimeout?.inWholeMilliseconds
                    socketTimeoutMillis = httpConfiguration.socketTimeout?.inWholeMilliseconds
                }
                defaultRequest {
                    url(baseUrl)
                }
            }
            val httpClient =
                if (engine == null) HttpClient(configure) else HttpClient(engine, configure)
            return KoniferClient(
                httpClient = httpClient,
                urlSigner = urlSigner,
            )
        }

        /**
         * Creates a client that uses an existing HTTP client supplied by an internal test harness.
         *
         * The supplied client must include any test-server configuration required to route requests.
         */
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

    private val requestInfrastructure: RequestInfrastructure =
        RequestInfrastructure(
            httpClient = httpClient,
            urlSigner = urlSigner,
        )

    /** Selects the assets stored at [path]. */
    fun assets(path: String): AssetAtPath =
        AssetAtPath(
            infra = requestInfrastructure,
            path = path,
        )

    /** Starts a rule evaluation by selecting an image source. */
    fun ruleEvaluation(): BlankRuleEvaluation = BlankRuleEvaluation(requestInfrastructure)

    /** Closes this client's HTTP resources, but not an engine supplied to [build]. */
    fun close() {
        requestInfrastructure.close()
    }
}
