package io.direkt.asset.handler.dto

import io.direkt.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.LQIPs
import io.image.model.Transformation

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
)
