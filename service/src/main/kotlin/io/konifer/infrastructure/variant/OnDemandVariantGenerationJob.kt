package io.konifer.infrastructure.variant

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.ports.ContentProcessorResult
import io.konifer.domain.ports.TransformationDataContainer
import io.konifer.domain.rules.RuleDefinition
import io.konifer.domain.rules.upload.UploadRuleset
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

sealed interface ImageProcessingJob<T> {
    val deferredResult: CompletableDeferred<T>?
}

data class PreProcessJob(
    val source: Path,
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformationDataContainer: TransformationDataContainer,
    override val deferredResult: CompletableDeferred<Unit>,
) : ImageProcessingJob<Unit>

data class GenerateVariantsJob(
    val source: Path,
    val transformationDataContainers: List<TransformationDataContainer>,
    val lqipImplementations: Set<LQIPImplementation>,
    override val deferredResult: CompletableDeferred<Unit>,
) : ImageProcessingJob<Unit>

data class ProcessOriginalVariantContentJob(
    val source: Path,
    val sourceFormat: ImageFormat,
    val lqipImplementations: Set<LQIPImplementation>,
    val transformationDataContainer: TransformationDataContainer,
    val uploadRuleset: UploadRuleset,
    override val deferredResult: CompletableDeferred<ContentProcessorResult>,
) : ImageProcessingJob<ContentProcessorResult>

data class ImageTensor(
    val values: FloatArray,
    val shape: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageTensor

        if (!values.contentEquals(other.values)) return false
        if (!shape.contentEquals(other.shape)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = values.contentHashCode()
        result = 31 * result + shape.contentHashCode()
        return result
    }
}
