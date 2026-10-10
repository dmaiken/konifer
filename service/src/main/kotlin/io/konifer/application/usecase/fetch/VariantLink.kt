package io.konifer.application.usecase.fetch

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AttributeResponse
import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.infrastructure.http.fromAttributes

data class VariantLink(
    val path: String,
    val deliveryUrl: DeliveryUrl,
    val entryId: Long,
    val lqip: LQIPs,
    val alt: String?,
    val cacheHit: Boolean,
    val attributes: Attributes,
    val redirectEnabled: Boolean = false,
) {
    fun toResponse(): AssetLinkResponse =
        AssetLinkResponse(
            url = deliveryUrl.url.toString(),
            expiresAt = deliveryUrl.expiresAt,
            alt = alt,
            lqip = lqip.toResponse(),
            attributes = AttributeResponse.fromAttributes(attributes),
        )
}
