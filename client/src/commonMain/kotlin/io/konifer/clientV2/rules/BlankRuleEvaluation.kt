package io.konifer.clientV2.rules

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.rules.RuleEvaluationSource.Channel
import io.konifer.clientV2.rules.RuleEvaluationSource.Bytes
import io.konifer.clientV2.rules.RuleEvaluationSource.S3Arn
import io.konifer.clientV2.rules.RuleEvaluationSource.Url
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel

class BlankRuleEvaluation internal constructor(
    private val infra: RequestInfrastructure,
) {

    fun fromUrl(url: String): NewRuleEvaluation = NewRuleEvaluation(infra, Url(url))

    fun fromS3Arn(s3Arn: String): NewRuleEvaluation = NewRuleEvaluation(infra, S3Arn(s3Arn))

    fun fromBytes(
        bytes: ByteArray,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Bytes(bytes, format))

    fun fromChannel(
        channel: ByteReadChannel,
        format: ImageFormat,
    ): NewRuleEvaluation = NewRuleEvaluation(infra, Channel(channel, format))
}
