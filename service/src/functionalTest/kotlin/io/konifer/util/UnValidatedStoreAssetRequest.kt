package io.konifer.util

import kotlinx.serialization.Serializable

@Serializable
data class UnValidatedStoreAssetRequest(
    val alt: String? = null,
    val url: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
)
