package io.konifer.clientV2.assets.store

import io.konifer.common.http.StoreAssetRequest

internal data class AssetRequestBuilder(
    val assetSource: AssetContentSource,
    val alt: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
) {
    fun build(): StoreAssetRequest {
        val requestWithoutContent =
            StoreAssetRequest(
                alt = this.alt,
                labels = this.labels,
                tags = this.tags,
            )

        return if (assetSource is AssetReferenceContentSource) {
            assetSource.applyToRequest(requestWithoutContent)
        } else {
            requestWithoutContent
        }
    }
}
