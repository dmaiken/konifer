package io.konifer.application.usecase.fetch

import io.konifer.common.http.AssetLinkResponse
import io.konifer.domain.variant.LQIPs

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
