package io.direkt.asset.handler.dto

import io.direkt.domain.asset.AssetSource
import io.direkt.domain.ports.PersistResult
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.infrastructure.StoreAssetRequest

data class StoreAssetDto(
    val path: String,
    val request: StoreAssetRequest,
    val attributes: Attributes,
    val persistResult: PersistResult,
    val lqips: LQIPs,
    val source: AssetSource,
)
