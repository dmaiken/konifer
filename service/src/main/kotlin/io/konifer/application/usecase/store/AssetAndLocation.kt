package io.konifer.application.usecase.store

import io.konifer.domain.asset.Asset

data class AssetAndLocation(
    val asset: Asset,
    val locationPath: String,
)
