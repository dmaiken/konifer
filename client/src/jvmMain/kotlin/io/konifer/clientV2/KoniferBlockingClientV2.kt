package io.konifer.clientV2

import io.konifer.clientV2.assets.BlockingAssetAtPath
import io.konifer.clientV2.rules.BlockingBlankRuleEvaluation
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking

/** JVM entry point for callers that cannot use suspending functions. */
class KoniferBlockingClientV2 internal constructor(
    private val client: KoniferClientV2,
) : AutoCloseable {
    companion object {
        /**
         * Creates a blocking client with the same configuration as [KoniferClientV2.build].
         * A supplied [engine] remains caller-owned and must be closed separately.
         */
        @JvmStatic
        @JvmOverloads
        fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
            httpConfiguration: KoniferHttpConfiguration = KoniferHttpConfiguration.Default,
            engine: HttpClientEngine? = null,
        ): KoniferBlockingClientV2 =
            KoniferBlockingClientV2(
                runBlocking { KoniferClientV2.build(baseUrl, hmacKey, hmacSigningAlgorithm, httpConfiguration, engine) },
            )
    }

    fun assets(path: String): BlockingAssetAtPath = BlockingAssetAtPath(client.assets(path))

    fun ruleEvaluation(): BlockingBlankRuleEvaluation = BlockingBlankRuleEvaluation(client.ruleEvaluation())

    override fun close() {
        client.close()
    }
}
