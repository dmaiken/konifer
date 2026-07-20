package io.konifer.infrastructure.variant.original

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.OriginalVariantContentProcessor
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.UploadRuleDecision
import io.konifer.domain.rules.upload.UploadRuleset
import io.konifer.infrastructure.work.ProcessOriginalVariantContentWorkItem
import io.konifer.infrastructure.work.WorkItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path

class ChannelOriginalVariantContentScheduler(
    private val highPriorityChannel: Channel<WorkItem<*>>,
) : OriginalVariantContentProcessor {
    override suspend fun process(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        source: Path,
        transformationDataContainer: TransformationDataContainer,
        uploadRuleset: UploadRuleset,
    ): CompletableDeferred<UploadRuleDecision> {
        val deferred = CompletableDeferred<UploadRuleDecision>()
        highPriorityChannel.send(
            ProcessOriginalVariantContentWorkItem(
                source = source,
                sourceFormat = sourceFormat,
                lqipImplementations = lqipImplementations,
                transformationDataContainer = transformationDataContainer,
                uploadRuleset = uploadRuleset,
                deferredResult = deferred,
            ),
        )

        return deferred
    }
}
