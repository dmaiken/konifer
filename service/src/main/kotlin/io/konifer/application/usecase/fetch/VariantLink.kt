package io.konifer.application.usecase.fetch

import io.konifer.common.http.AssetLinkResponse
import io.konifer.domain.variant.LQIPs
import io.ktor.http.Url

data class VariantLink(
    val path: String,
    val url: Url,
    val entryId: Long,
    val lqip: LQIPs,
    val alt: String?,
    val cacheHit: Boolean,
    val redirectEnabled: Boolean = false,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = url.toString(),
            alt = alt,
            lqip = lqip.toResponse(),
        )
}
