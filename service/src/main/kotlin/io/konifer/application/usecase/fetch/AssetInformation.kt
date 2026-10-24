package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.projection.AssetData

data class AssetInformation(
    val asset: AssetData,
    val cacheResult: CacheResult,
)
