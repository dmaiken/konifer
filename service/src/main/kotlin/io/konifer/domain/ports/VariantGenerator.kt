package io.konifer.domain.ports

import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.variant.Attributes
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.Transformation
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

interface VariantGenerator {
    suspend fun generateVariantsFromSource(
        source: Path,
        transformationDataContainers: List<TransformationDataContainer>,
        lqipImplementations: Set<LQIPImplementation>,
        variantType: VariantType,
    ): CompletableDeferred<Unit>
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
