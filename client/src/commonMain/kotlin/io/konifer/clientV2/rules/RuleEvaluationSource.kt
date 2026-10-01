package io.konifer.clientV2.rules

import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.HttpSource
import io.konifer.common.http.S3Source
import io.konifer.common.image.ImageFormat
import io.ktor.utils.io.ByteReadChannel

internal sealed interface RuleEvaluationSource {
    sealed interface External : RuleEvaluationSource {
        fun toRequestSource(): AssetSourceRequest
    }

    data class Url(
        val value: String,
    ) : External {
        override fun toRequestSource(): AssetSourceRequest = AssetSourceRequest(http = HttpSource(url = value))
    }

    data class S3Arn(
        val value: String,
    ) : External {
        override fun toRequestSource(): AssetSourceRequest = AssetSourceRequest(s3 = S3Source(arn = value))
    }

    sealed interface Upload : RuleEvaluationSource {
        val format: ImageFormat

        fun channel(): ByteReadChannel
    }

    data class Bytes(
        private val value: ByteArray,
        override val format: ImageFormat,
    ) : Upload {
        override fun channel(): ByteReadChannel = ByteReadChannel(value)
    }

    data class Channel(
        private val value: ByteReadChannel,
        override val format: ImageFormat,
    ) : Upload {
        override fun channel(): ByteReadChannel = value
    }
}
