package io.konifer.infrastructure.datastore.inmemory.projection

import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import java.time.Instant

class InMemoryAssetDeliveryProjection(
    assetData: AssetData,
) : AssetDeliveryProjection {
    override val id: AssetId = assetData.id
    override val alt: String? = assetData.alt
    override val entryId: Long = checkNotNull(assetData.entryId)
    override val path: String = assetData.path
    override val modifiedAt: Instant = assetData.modifiedAt
    override val variants: List<VariantDeliveryProjection> = assetData.variants.map(::InMemoryVariantDeliveryProjection)
}
