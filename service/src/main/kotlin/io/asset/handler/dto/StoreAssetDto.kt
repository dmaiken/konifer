package io.asset.handler.dto

import io.asset.handler.AssetSource
import io.asset.model.StoreAssetRequest
import io.asset.store.PersistResult
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
