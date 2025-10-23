package io.asset.handler

import io.asset.model.AssetAndVariants

data class AssetAndLocation(
    val assetAndVariants: AssetAndVariants,
    val locationPath: String,
)
