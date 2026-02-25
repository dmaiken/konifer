package io.konifer.infrastructure.variant

import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.image.PreProcessedImage
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.variant.Transformation
import kotlinx.coroutines.CompletableDeferred
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
    val source: Path,
    val transformationDataContainers: List<TransformationDataContainer>,
    val lqipImplementations: Set<LQIPImplementation>,
    override val deferredResult: CompletableDeferred<Boolean>,
) : ImageProcessingJob<Boolean>
