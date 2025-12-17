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
import java.nio.file.Path

sealed interface ImageProcessingJob<T> {
    val deferredResult: CompletableDeferred<T>?
}

data class PreProcessJob(
    val source: Path,
    val output: Path,
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformation: Transformation,
    override val deferredResult: CompletableDeferred<PreProcessedImage>,
) : ImageProcessingJob<PreProcessedImage>

data class GenerateVariantsJob(
    val source: File,
    val transformationDataContainers: List<TransformationDataContainer>,
    val lqipImplementations: Set<LQIPImplementation>,
    override val deferredResult: CompletableDeferred<Boolean>
) : ImageProcessingJob<Boolean>
