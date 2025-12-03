package io.direkt.asset.handler.dto

import io.direkt.asset.handler.AssetSource
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.LQIPs

data class StoreAssetDto(
    val path: String,
    val request: StoreAssetRequest,
    val attributes: Attributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
    val source: AssetSource,
)
