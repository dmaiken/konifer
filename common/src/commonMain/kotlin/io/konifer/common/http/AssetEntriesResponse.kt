package io.konifer.common.http

import kotlinx.serialization.Serializable

@Serializable
data class AssetEntriesResponse(
    val entries: List<AssetResponse>,
)
