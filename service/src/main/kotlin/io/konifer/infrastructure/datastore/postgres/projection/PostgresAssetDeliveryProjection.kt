package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.datastore.postgres.AssetRecordsDto
import io.konifer.infrastructure.datastore.postgres.LtreePathAdapter
import java.time.Instant

class PostgresAssetDeliveryProjection(
    assetRecordsDto: AssetRecordsDto,
) : AssetDeliveryProjection {
    override val id: AssetId = AssetId(checkNotNull(assetRecordsDto.asset.id))
    override val alt: String? = assetRecordsDto.asset.alt
    override val entryId: Long = checkNotNull(assetRecordsDto.asset.entryId)
    override val path: String = LtreePathAdapter.toUriPath(checkNotNull(assetRecordsDto.asset.path).data())
    override val modifiedAt: Instant = checkNotNull(assetRecordsDto.asset.modifiedAt).toInstant()
    override val variants: List<VariantDeliveryProjection> = assetRecordsDto.variants.map(::PostgresVariantDeliveryProjection)
}
