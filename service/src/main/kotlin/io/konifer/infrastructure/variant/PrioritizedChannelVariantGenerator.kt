package io.konifer.infrastructure.variant

import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.ports.VariantGenerator
import io.konifer.domain.ports.VariantType
import io.konifer.domain.variant.Transformation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path

class PrioritizedChannelVariantGenerator(
    private val highPriorityChannel: Channel<ImageProcessingJob<*>>,
    private val backgroundChannel: Channel<ImageProcessingJob<*>>,
) : VariantGenerator {
    override suspend fun generateVariantsFromSource(
        source: Path,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
        variantType: VariantType,
    ): CompletableDeferred<Unit> {
        if (transformationDataContainers.isEmpty()) {
            return CompletableDeferred(Unit)
        }
        if (transformationDataContainers.all { it.transformation == Transformation.ORIGINAL_VARIANT }) {
            throw IllegalArgumentException("Cannot create variant using original variant transformation")
        }
        val deferred = CompletableDeferred<Unit>()
        val job =
            GenerateVariantsJob(
                source = source,
                transformationDataContainers = transformationDataContainers,
                lqipImplementations = lqipImplementations,
                deferredResult = deferred,
            )
        when (variantType) {
            VariantType.EAGER -> backgroundChannel.send(job)
            VariantType.ON_DEMAND -> highPriorityChannel.send(job)
        }
        return deferred
    }
}
