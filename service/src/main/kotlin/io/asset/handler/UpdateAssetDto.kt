package io.asset.handler

import io.asset.model.StoreAssetRequest

data class UpdateAssetDto(
    val path: String,
    val entryId: Long,
    val request: StoreAssetRequest,
)
