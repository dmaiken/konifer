package io.direkt.asset.handler.dto

import io.direkt.domain.asset.AssetData

data class AssetMetadataDto(
    val asset: AssetData,
    val cacheHit: Boolean,
)
