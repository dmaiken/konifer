package io.direkt.domain.variant

import io.direkt.infrastructure.http.AssetLinkResponse

data class VariantLink(
    val url: String,
    val lqip: LQIPs,
    val alt: String?,
    val cacheHit: Boolean,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = url,
            alt = alt,
            lqip = lqip.toResponse(),
        )
}
