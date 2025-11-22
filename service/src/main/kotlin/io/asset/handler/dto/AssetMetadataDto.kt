package io.asset.handler.dto

import io.asset.model.AssetAndVariants

data class AssetMetadataDto(
    val asset: AssetAndVariants,
    val cacheHit: Boolean,
)
