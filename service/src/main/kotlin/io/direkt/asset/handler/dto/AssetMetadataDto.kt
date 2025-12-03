package io.direkt.asset.handler.dto

import io.direkt.asset.model.AssetAndVariants

data class AssetMetadataDto(
    val asset: AssetAndVariants,
    val cacheHit: Boolean,
)
