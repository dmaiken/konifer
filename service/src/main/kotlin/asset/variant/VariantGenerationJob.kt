package io.asset.variant

import asset.model.AssetAndVariants
import image.model.RequestedImageAttributes
import kotlinx.coroutines.CompletableDeferred

data class VariantGenerationJob(
    val treePath: String,
    val entryId: Long,
    val newVariantBucket: String,
    val requestedImageAttributes: List<RequestedImageAttributes>,
    val deferredResult: CompletableDeferred<AssetAndVariants>? = null,
)
