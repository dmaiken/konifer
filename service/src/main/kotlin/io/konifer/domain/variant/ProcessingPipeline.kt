package io.konifer.domain.variant

import io.konifer.domain.ports.ContentProcessorResult
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.Deferred
import java.nio.file.Path

data class ProcessingPipeline(
    val attributes: Deferred<Attributes>,
    val outputChannel: ByteChannel,
    val eagerVariantFile: Path?, // Null if eager variants aren't needed
    val processDeferred: Deferred<ContentProcessorResult>,
    val lqips: Deferred<LQIPs?>,
)
