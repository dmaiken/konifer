package io.direkt.infrastructure.variant

import io.direkt.asset.model.AssetAndVariants
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.ports.VariantGenerator
import io.direkt.domain.variant.Transformation
import io.direkt.service.context.RequestedTransformation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.io.File

class PrioritizedChannelVariantScheduler(
    private val highPriorityChannel: Channel<ImageProcessingJob<*>>,
    private val backgroundChannel: Channel<ImageProcessingJob<*>>,
) : VariantGenerator {
    override suspend fun preProcessOriginalVariant(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        source: File,
    ): CompletableDeferred<PreProcessedImage> {
        val deferred = CompletableDeferred<PreProcessedImage>()
        highPriorityChannel.send(
            PreProcessJob(
                sourceFormat = sourceFormat,
                transformation = transformation,
                source = source,
                lqipImplementations = lqipImplementations,
                deferredResult = deferred,
            ),
        )
        return deferred
    }

    override suspend fun initiateEagerVariants(
        path: String,
        entryId: Long,
        requestedTransformations: List<RequestedTransformation>,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
    ) {
        backgroundChannel.send(
            EagerVariantGenerationJob(
                path = path,
                entryId = entryId,
                lqipImplementations = lqipImplementations,
                bucket = bucket,
                requestedTransformations = requestedTransformations,
                deferredResult = null,
            ),
        )
    }

    override suspend fun generateOnDemandVariant(
        path: String,
        entryId: Long,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
        transformation: Transformation,
    ): CompletableDeferred<AssetAndVariants> {
        val deferred = CompletableDeferred<AssetAndVariants>()
        highPriorityChannel.send(
            OnDemandVariantGenerationJob(
                path = path,
                entryId = entryId,
                lqipImplementations = lqipImplementations,
                bucket = bucket,
                transformation = transformation,
                deferredResult = deferred,
            ),
        )
        return deferred
    }
}
