package io.asset.model

import kotlinx.serialization.Serializable

@Serializable
data class StoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
)
