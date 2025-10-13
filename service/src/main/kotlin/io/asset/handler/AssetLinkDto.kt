package io.asset.handler

import io.asset.model.AssetLinkResponse
import io.image.model.LQIPs

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
