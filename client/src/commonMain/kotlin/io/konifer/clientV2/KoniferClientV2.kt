package io.konifer.clientV2

import io.konifer.clientV2.assets.AssetAtPath
import io.konifer.clientV2.internal.HmacSigningAlgorithm
import io.konifer.clientV2.internal.HmacSigningConfig
import io.konifer.clientV2.internal.KoniferUrlSigner
import io.konifer.clientV2.rules.BlankRuleEvaluation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KoniferClientV2 internal constructor(
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
        ): KoniferClientV2 {
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
            return KoniferClientV2(
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
        ): KoniferClientV2 =
            KoniferClientV2(
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

    private val requestInfrastructure =
        RequestInfrastructure(
            httpClient = httpClient,
            urlSigner = urlSigner,
        )

    fun asset(path: String): AssetAtPath =
        AssetAtPath(
            infra = requestInfrastructure,
            path = path,
        )

    fun ruleEvaluation(): BlankRuleEvaluation = BlankRuleEvaluation(requestInfrastructure)

    fun close() {
        httpClient.close()
    }
}
