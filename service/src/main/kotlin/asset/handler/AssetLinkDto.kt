package io.asset.handler

import asset.model.AssetLinkResponse
import image.model.LQIPs

data class AssetLinkDto(
    val url: String,
    val cacheHit: Boolean,
    val lqip: LQIPs,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = url,
            lqip = lqip.toResponse(),
        )
}
