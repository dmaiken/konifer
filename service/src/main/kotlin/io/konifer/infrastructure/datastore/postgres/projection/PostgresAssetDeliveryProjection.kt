package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.AssetPath
import io.konifer.domain.asset.EntryId
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.asset.toEntryId
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.datastore.postgres.AssetRecordsDto
import io.konifer.infrastructure.datastore.postgres.toAssetPath
import java.time.Instant

class PostgresAssetDeliveryProjection(
    assetRecordsDto: AssetRecordsDto,
) : AssetDeliveryProjection {
    override val id: AssetId = AssetId(checkNotNull(assetRecordsDto.asset.id))
    override val alt: String? = assetRecordsDto.asset.alt
    override val entryId: EntryId = checkNotNull(assetRecordsDto.asset.entryId).toEntryId()
    override val path: AssetPath = checkNotNull(assetRecordsDto.asset.path).toAssetPath()
    override val modifiedAt: Instant = checkNotNull(assetRecordsDto.asset.modifiedAt).toInstant()
    override val variants: List<VariantDeliveryProjection> = assetRecordsDto.variants.map(::PostgresVariantDeliveryProjection)
}
