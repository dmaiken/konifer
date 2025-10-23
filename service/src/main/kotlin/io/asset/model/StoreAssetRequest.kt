package io.asset.model

import kotlinx.serialization.Serializable

@Serializable
data class StoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
)
