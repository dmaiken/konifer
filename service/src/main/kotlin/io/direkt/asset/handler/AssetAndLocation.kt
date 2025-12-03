package io.direkt.asset.handler

import io.direkt.asset.model.AssetAndVariants

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
