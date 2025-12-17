package io.direkt.infrastructure.variant

import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.ports.VariantType
import io.direkt.domain.variant.Transformation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.nio.file.Path

class PrioritizedChannelVariantScheduler(
    private val highPriorityChannel: Channel<ImageProcessingJob<*>>,
    private val backgroundChannel: Channel<ImageProcessingJob<*>>,
) : VariantGenerator {

    override suspend fun preProcessOriginalVariant(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        source: Path,
        output: Path,
    ): CompletableDeferred<PreProcessedImage> {
        val deferred = CompletableDeferred<PreProcessedImage>()
        highPriorityChannel.send(
            PreProcessJob(
                sourceFormat = sourceFormat,
                transformation = transformation,
                source = source,
                output = output,
                lqipImplementations = lqipImplementations,
                deferredResult = deferred,
            ),
        )
        return deferred
    }

    override suspend fun generateVariantsFromSource(
        source: File,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
        variantType: VariantType,
    ): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        val job = GenerateVariantsJob(
            source = source,
            transformationDataContainers = transformationDataContainers,
            lqipImplementations = lqipImplementations,
            deferredResult = deferred
        )
        when (variantType) {
            VariantType.EAGER -> backgroundChannel.send(job)
            VariantType.ON_DEMAND -> highPriorityChannel.send(job)
        }
        return deferred
    }
}
