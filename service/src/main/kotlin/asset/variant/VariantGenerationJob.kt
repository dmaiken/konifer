package io.asset.variant

import asset.model.AssetAndVariants
import image.model.Transformation
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.CompletableDeferred

data class VariantGenerationJob(
    val treePath: String,
    val entryId: Long,
    val pathConfiguration: PathConfiguration,
    val transformations: List<Transformation>,
    val deferredResult: CompletableDeferred<AssetAndVariants>? = null,
)
