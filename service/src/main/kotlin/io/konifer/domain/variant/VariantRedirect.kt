package io.konifer.domain.variant

import io.konifer.domain.asset.AssetData

data class VariantRedirect(
    val url: String?,
    val asset: AssetData,
    val variant: VariantData,
    val cacheHit: Boolean,
)
