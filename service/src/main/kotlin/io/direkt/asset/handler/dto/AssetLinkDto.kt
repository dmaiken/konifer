package io.direkt.asset.handler.dto

import io.direkt.domain.variant.LQIPs
import io.direkt.infrastructure.http.AssetLinkResponse

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
