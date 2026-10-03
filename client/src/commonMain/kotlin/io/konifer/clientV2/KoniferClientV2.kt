package io.konifer.clientV2

import io.konifer.clientV2.assets.AssetAtPath
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.clientV2.rules.BlankRuleEvaluation
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientV2 internal constructor(
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
         */
        suspend fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
            httpConfiguration: KoniferHttpConfiguration = KoniferHttpConfiguration.Default,
            engine: HttpClientEngine? = null,
        ): KoniferClientV2 {
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
            return KoniferClientV2(
                httpClient = httpClient,
                urlSigner = urlSigner,
            )
        }
    }

    private val requestInfrastructure: RequestInfrastructure =
        RequestInfrastructure(
            httpClient = httpClient,
            urlSigner = urlSigner,
        )

    fun assets(path: String): AssetAtPath =
        AssetAtPath(
            infra = requestInfrastructure,
            path = path,
        )

    fun ruleEvaluation(): BlankRuleEvaluation = BlankRuleEvaluation(requestInfrastructure)

    /** Closes this client's HTTP resources, but not an engine supplied to [build]. */
    fun close() {
        requestInfrastructure.close()
    }
}
