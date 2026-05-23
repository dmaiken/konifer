package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetData

data class AssetInformation(
    val asset: AssetData,
    val cacheHit: Boolean,
)
