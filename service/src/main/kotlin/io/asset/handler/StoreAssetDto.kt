package io.asset.handler

import io.asset.model.StoreAssetRequest
import io.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.LQIPs

class StoreAssetDto(
    val path: String,
    val request: StoreAssetRequest,
    val attributes: Attributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
    val labels: Map<String, String> = emptyMap(),
)
