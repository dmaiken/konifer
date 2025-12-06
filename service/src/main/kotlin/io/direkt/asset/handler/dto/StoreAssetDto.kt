package io.direkt.asset.handler.dto

import io.direkt.domain.asset.AssetSource
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.domain.ports.PersistResult
import io.direkt.domain.image.Attributes
import io.direkt.domain.image.LQIPs

data class StoreAssetDto(
    val path: String,
    val request: StoreAssetRequest,
    val attributes: Attributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
    val source: AssetSource,
)
