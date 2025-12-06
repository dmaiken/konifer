package io.direkt.asset.handler.dto

import io.direkt.domain.ports.PersistResult
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
)
