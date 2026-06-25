package io.konifer.infrastructure.variant.original

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.ContentProcessorResult
import io.konifer.domain.ports.OriginalVariantContentProcessor
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.infrastructure.variant.ImageProcessingJob
import io.konifer.infrastructure.variant.ProcessOriginalVariantContentJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path

class ChannelOriginalVariantContentScheduler(
    private val highPriorityChannel: Channel<ImageProcessingJob<*>>,
) : OriginalVariantContentProcessor {

    override suspend fun process(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        source: Path,
        transformationDataContainer: TransformationDataContainer,
        uploadRuleset: UploadRuleset,
    ): CompletableDeferred<ContentProcessorResult> {
        val deferred = CompletableDeferred<ContentProcessorResult>()
        highPriorityChannel.send(
            ProcessOriginalVariantContentJob(
                source = source,
                sourceFormat = sourceFormat,
                lqipImplementations = lqipImplementations,
                transformationDataContainer = transformationDataContainer,
                uploadRuleset = uploadRuleset,
                deferredResult = deferred,
            )
        )

        return deferred
    }
}
