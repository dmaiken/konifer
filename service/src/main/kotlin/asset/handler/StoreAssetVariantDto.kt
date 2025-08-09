package io.asset.handler

import asset.store.PersistResult
import image.model.ImageAttributes
import image.model.LQIPs

data class StoreAssetVariantDto(
    val path: String,
    val entryId: Long,
    val persistResult: PersistResult,
    val imageAttributes: ImageAttributes,
    val lqips: LQIPs,
)
