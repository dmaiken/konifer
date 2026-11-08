package io.konifer.application.usecase.store

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetPath

data class AssetAndLocation(
    val asset: Asset,
    val locationPath: AssetPath,
)
