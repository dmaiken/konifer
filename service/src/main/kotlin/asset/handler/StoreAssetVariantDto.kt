package io.asset.handler

import asset.store.PersistResult
import image.model.Attributes
import image.model.LQIPs
import image.model.Transformation

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val attributes: Attributes,
    val transformation: Transformation,
    val lqips: LQIPs,
)
