package io.direkt.infrastructure.variant

import io.direkt.asset.model.AssetAndVariants
import io.direkt.image.model.LQIPImplementation
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.PreProcessedImage
import io.direkt.image.model.RequestedTransformation
import io.direkt.image.model.Transformation
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
    override val deferredResult: CompletableDeferred<AssetAndVariants>,
) : ImageProcessingJob<AssetAndVariants>

data class EagerVariantGenerationJob(
    val path: String,
    val entryId: Long,
    val lqipImplementations: Set<LQIPImplementation>,
    val bucket: String,
    val requestedTransformations: List<RequestedTransformation>,
    override val deferredResult: CompletableDeferred<AssetAndVariants>? = null,
) : ImageProcessingJob<AssetAndVariants>

data class PreProcessJob(
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformation: Transformation,
    val source: File,
    override val deferredResult: CompletableDeferred<PreProcessedImage>,
) : ImageProcessingJob<PreProcessedImage>
