package io.direkt.asset.handler.dto

import io.direkt.asset.store.PersistResult
import io.direkt.image.model.Attributes
import io.direkt.image.model.LQIPs
import io.direkt.image.model.Transformation

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
)
