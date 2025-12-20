package io.direkt.domain.ports

import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
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
    val output: Path,
) {
    var lqips: LQIPs = LQIPs.NONE
    var attributes: Attributes? = null
}
