package io.direkt.asset.handler.dto

import io.direkt.domain.ports.PersistResult
import io.direkt.domain.image.Attributes
import io.direkt.domain.image.LQIPs
import io.direkt.domain.image.Transformation

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
)
