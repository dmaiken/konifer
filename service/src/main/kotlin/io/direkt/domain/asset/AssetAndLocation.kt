package io.direkt.domain.asset

import io.direkt.asset.model.AssetAndVariants

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
