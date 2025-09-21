package io.asset.variant

import asset.model.AssetAndVariants
import image.model.RequestedImageTransformation
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.CompletableDeferred

data class VariantGenerationJob(
    val treePath: String,
    val entryId: Long,
    val pathConfiguration: PathConfiguration,
    val transformations: List<RequestedImageTransformation>,
    val deferredResult: CompletableDeferred<AssetAndVariants>? = null,
)
