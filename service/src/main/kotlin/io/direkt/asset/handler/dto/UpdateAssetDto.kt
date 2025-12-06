package io.direkt.asset.handler.dto

import io.direkt.infrastructure.StoreAssetRequest

data class UpdateAssetDto(
    val path: String,
    val entryId: Long,
    val request: StoreAssetRequest,
)
