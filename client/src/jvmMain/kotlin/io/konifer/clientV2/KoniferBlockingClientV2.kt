package io.konifer.clientV2

import io.konifer.clientV2.internal.HmacSigningAlgorithm
import io.konifer.clientV2.rules.BlockingBlankRuleEvaluation
import kotlinx.coroutines.runBlocking

/** JVM entry point for callers that cannot use suspending functions. */
class KoniferBlockingClientV2 internal constructor(
    private val client: KoniferClientV2,
) : AutoCloseable {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun build(
            baseUrl: String,
            hmacKey: String? = null,
            hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
        ): KoniferBlockingClientV2 =
            KoniferBlockingClientV2(
                runBlocking { KoniferClientV2.build(baseUrl, hmacKey, hmacSigningAlgorithm) },
            )
    }

    fun assets(path: String): BlockingAssetAtPath = BlockingAssetAtPath(client.assets(path))

    fun ruleEvaluation(): BlockingBlankRuleEvaluation = BlockingBlankRuleEvaluation(client.ruleEvaluation())

    override fun close() {
        client.close()
    }
}
