package io.konifer.application.usecase.fetch

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AttributesResponse
import io.konifer.domain.asset.AssetPath
import io.konifer.domain.asset.EntryId
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.http.fromAttributes

data class VariantLink(
    val path: AssetPath,
    val deliveryUrl: DeliveryUrl,
    val entryId: EntryId,
    val lqip: LQIPs,
    val alt: String?,
    val cacheResult: CacheResult,
    val attributes: Attributes,
    val redirectEnabled: Boolean = false,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = deliveryUrl.url.toString(),
            expiresAt = deliveryUrl.expiresAt,
            alt = alt,
            lqip = lqip.toResponse(),
            attributes = AttributesResponse.fromAttributes(attributes),
        )
}
