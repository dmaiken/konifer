package io.direkt.asset.handler.dto

import io.direkt.asset.model.AssetLinkResponse
import io.direkt.domain.image.LQIPs

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
