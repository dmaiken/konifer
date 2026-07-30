package io.konifer.domain.variant

import io.konifer.domain.rules.UploadRuleDecision
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.Deferred
import java.nio.file.Path

data class ProcessingPipeline(
    val attributes: Deferred<Attributes>,
    val outputChannel: ByteChannel,
    val eagerVariantFile: Path?, // Null if eager variants aren't needed
    val processDeferred: Deferred<UploadRuleDecision>,
    val lqips: Deferred<LQIPs?>,
)
