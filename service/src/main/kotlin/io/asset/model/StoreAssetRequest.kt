package io.asset.model

import kotlinx.serialization.Serializable

@Serializable
data class StoreAssetRequest(
    val type: String,
    val alt: String? = null,
)
