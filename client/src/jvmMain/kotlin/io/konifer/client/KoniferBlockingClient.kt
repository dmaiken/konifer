package io.konifer.client

import io.konifer.client.assets.BlockingAssetAtPath
import io.konifer.client.rules.BlockingBlankRuleEvaluation
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking

/** JVM client for callers that cannot use suspending functions. */
class KoniferBlockingClient internal constructor(
    private val client: KoniferClient,
) : AutoCloseable {
    companion object {
        /**
         * Creates a blocking client with the same configuration as [KoniferClient.build].
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
        ): KoniferBlockingClient =
            KoniferBlockingClient(
                runBlocking { KoniferClient.build(baseUrl, hmacKey, hmacSigningAlgorithm, httpConfiguration, engine) },
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
