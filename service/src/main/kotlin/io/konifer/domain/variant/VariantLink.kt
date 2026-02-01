package io.konifer.domain.variant

import io.konifer.infrastructure.http.AssetLinkResponse

data class VariantLink(
    val path: String,
    val url: String,
    val entryId: Long,
    val lqip: LQIPs,
    val alt: String?,
    val cacheHit: Boolean,
    val redirectEnabled: Boolean = false,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = url,
            alt = alt,
            lqip = lqip.toResponse(),
        )
}
