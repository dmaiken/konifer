package io.asset.variant.generation

import io.asset.AssetStreamContainer
import io.asset.model.AssetAndVariants
import io.image.lqip.LQIPImplementation
import io.image.model.ImageFormat
import io.image.model.PreProcessedImage
import io.image.model.RequestedImageTransformation
import io.image.model.Transformation
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred

sealed interface ImageProcessingJob<T> {
    val deferredResult: CompletableDeferred<T>?
}

data class VariantGenerationJob(
    val treePath: String,
    val entryId: Long?,
    val lqipImplementations: Set<LQIPImplementation>,
    val bucket: String,
    val transformations: List<Transformation>,
    override val deferredResult: CompletableDeferred<AssetAndVariants>,
) : ImageProcessingJob<AssetAndVariants>

data class EagerVariantGenerationJob(
    val treePath: String,
    val entryId: Long?,
    val lqipImplementations: Set<LQIPImplementation>,
    val bucket: String,
    val requestedTransformations: List<RequestedImageTransformation>,
    override val deferredResult: CompletableDeferred<AssetAndVariants>? = null,
) : ImageProcessingJob<AssetAndVariants>

data class PreProcessJob(
    val treePath: String,
    val sourceFormat: ImageFormat,
    val sourceContainer: AssetStreamContainer,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformation: Transformation,
    val outputChannel: ByteChannel,
    override val deferredResult: CompletableDeferred<PreProcessedImage>,
) : ImageProcessingJob<PreProcessedImage>
