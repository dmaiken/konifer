package io.konifer.clientV2.assets.store

import io.konifer.common.http.StoreAssetRequest

class AssetRequestBuilder(
    val assetSource: AssetContentSource,
) {
    var alt: String? = null
    val labels: MutableMap<String, String> = mutableMapOf()
    val tags: MutableSet<String> = mutableSetOf()

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
