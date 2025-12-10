package io.direkt.domain.variant

import io.direkt.infrastructure.http.AssetLinkResponse

data class VariantLink(
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
