package io.direkt.infrastructure.variant

import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.ports.TransformationDataContainer
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.service.context.RequestedTransformation
import kotlinx.coroutines.CompletableDeferred
import java.io.File

sealed interface ImageProcessingJob<T> {
    val deferredResult: CompletableDeferred<T>?
}

data class OnDemandVariantGenerationJob(
    val path: String,
    val entryId: Long,
    val lqipImplementations: Set<LQIPImplementation>,
    val bucket: String,
    val transformation: Transformation,
    override val deferredResult: CompletableDeferred<Variant>,
) : ImageProcessingJob<Variant>

data class EagerVariantGenerationJob(
    val path: String,
    val entryId: Long,
    val lqipImplementations: Set<LQIPImplementation>,
    val bucket: String,
    val requestedTransformations: List<RequestedTransformation>,
    override val deferredResult: CompletableDeferred<Nothing>? = null,
) : ImageProcessingJob<Nothing>

data class PreProcessJob(
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformation: Transformation,
    val source: File,
    override val deferredResult: CompletableDeferred<PreProcessedImage>,
) : ImageProcessingJob<PreProcessedImage>

data class GenerateVariantsJob(
    val source: File,
    val transformationDataContainers: List<TransformationDataContainer>,
    val lqipImplementations: Set<LQIPImplementation>,
    override val deferredResult: CompletableDeferred<Boolean>
) : ImageProcessingJob<Boolean>
