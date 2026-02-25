package io.konifer.domain.ports

import io.konifer.domain.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.image.PreProcessedImage
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

interface VariantGenerator {
    suspend fun preProcessOriginalVariant(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        source: Path,
        output: Path,
    ): CompletableDeferred<PreProcessedImage>

    suspend fun generateVariantsFromSource(
        source: Path,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
        variantType: VariantType,
    ): CompletableDeferred<Boolean>
}

enum class VariantType {
    EAGER,
    ON_DEMAND,
}

data class TransformationDataContainer(
    val transformation: Transformation,
    val output: ByteChannel = ByteChannel(),
    val lqips: CompletableDeferred<LQIPs?> = CompletableDeferred(),
    val attributes: CompletableDeferred<Attributes> = CompletableDeferred(),
)
