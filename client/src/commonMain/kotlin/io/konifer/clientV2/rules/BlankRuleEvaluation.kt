package io.konifer.clientV2.rules

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.internal.flowUploadChannel
import io.konifer.clientV2.rules.RuleEvaluationSource.Bytes
import io.konifer.clientV2.rules.RuleEvaluationSource.Channel
import io.konifer.clientV2.rules.RuleEvaluationSource.S3Arn
import io.konifer.clientV2.rules.RuleEvaluationSource.Url
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class BlankRuleEvaluation internal constructor(
    private val infra: RequestInfrastructure,
) {
    fun fromUrl(url: String): NewRuleEvaluation = NewRuleEvaluation(infra, Url(url))

    fun fromS3Arn(s3Arn: String): NewRuleEvaluation = NewRuleEvaluation(infra, S3Arn(s3Arn))

    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Bytes(bytes, format))

    /** [open] must return a fresh channel whenever an evaluation is sent or replayed. */
    fun fromChannel(
        open: () -> ByteReadChannel,
        format: ImageFormat,
    ): NewRuleEvaluation = fromChannelSource({ open() }, format)

    /** [chunks] must provide a new, collectable flow for each evaluation attempt. */
    fun fromChunks(
        chunks: () -> Flow<ByteArray>,
        format: ImageFormat,
    ): NewRuleEvaluation = fromChannelSource({ scope -> flowUploadChannel(scope, chunks) }, format)

    internal fun fromChannelSource(
        open: (CoroutineScope) -> ByteReadChannel,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Channel(open, format))
}
