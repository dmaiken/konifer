package io.konifer.clientV2

import io.konifer.clientV2.assets.BlockingAssetAtPath
import io.konifer.clientV2.rules.BlockingBlankRuleEvaluation
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking

/** JVM client for callers that cannot use suspending functions. */
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

    /** Selects the assets stored at [path]. */
    fun assets(path: String): BlockingAssetAtPath = BlockingAssetAtPath(client.assets(path))

    /** Starts a rule evaluation by selecting an image source. */
    fun ruleEvaluation(): BlockingBlankRuleEvaluation = BlockingBlankRuleEvaluation(client.ruleEvaluation())

    /** Closes this client's HTTP resources, but **not** an engine supplied to [build]. */
    override fun close() {
        client.close()
    }
}
