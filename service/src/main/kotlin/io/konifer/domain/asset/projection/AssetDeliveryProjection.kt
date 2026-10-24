package io.konifer.domain.asset.projection

import io.konifer.domain.asset.AssetId
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import java.time.Instant

interface AssetDeliveryProjection {
    val id: AssetId
    val path: String
    val entryId: Long
    val alt: String?

    val modifiedAt: Instant
    val variants: List<VariantDeliveryProjection>
}
