package io.konifer.client.rules

import io.konifer.client.internal.RequestInfrastructure
import io.konifer.client.internal.flowUploadChannel
import io.konifer.client.rules.RuleEvaluationSource.Bytes
import io.konifer.client.rules.RuleEvaluationSource.Channel
import io.konifer.client.rules.RuleEvaluationSource.S3Arn
import io.konifer.client.rules.RuleEvaluationSource.Url
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Selects the image source for a rule evaluation.
 *
 * Obtain this stage from [io.konifer.client.KoniferClient.ruleEvaluation]. After selecting a
 * source, add rule definitions and call [NewRuleEvaluation.evaluate].
 */
class BlankRuleEvaluation internal constructor(
    private val infra: RequestInfrastructure,
) {
    /**
     * Uses the image at [url] as the evaluation source.
     *
     * The Konifer server must allow the URL's domain through
     * `source.url.allowed-domains`.
     */
    fun fromUrl(url: String): NewRuleEvaluation = NewRuleEvaluation(infra, Url(url))

    /**
     * Uses the S3 object identified by [s3Arn] as the evaluation source.
     */
    fun fromS3Arn(s3Arn: String): NewRuleEvaluation = NewRuleEvaluation(infra, S3Arn(s3Arn))

    /**
     * Uses a snapshot of [bytes] as the evaluation source.
     *
     * This method copies [bytes], so later caller mutations do not affect
     * evaluations.
     *
     * @param format the format advertised for the uploaded bytes.
     */
    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Bytes(bytes, format))

    /**
     * Streams the image from a supplied channel.
     *
     * The HTTP client may invoke [provider] more than once. Each invocation
     * must return a fresh, readable channel containing the complete image.
     *
     * @param format the format advertised for the uploaded bytes.
     */
    fun fromChannel(
        provider: () -> ByteReadChannel,
        format: ImageFormat,
    ): NewRuleEvaluation = fromChannelSource({ provider() }, format)

    /**
     * Streams the image from byte-array chunks.
     *
     * The HTTP client may invoke [provider] and collect its result more than
     * once. Each invocation must supply a flow that supports an independent
     * collection of the complete image.
     *
     * @param format the format advertised for the uploaded bytes.
     */
    fun fromChunks(
        provider: () -> Flow<ByteArray>,
        format: ImageFormat,
    ): NewRuleEvaluation = fromChannelSource({ scope -> flowUploadChannel(scope, provider) }, format)

    private fun fromChannelSource(
        provider: (CoroutineScope) -> ByteReadChannel,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Channel(provider, format))
}
