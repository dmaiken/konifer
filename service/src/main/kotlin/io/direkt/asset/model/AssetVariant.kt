package io.direkt.asset.model

import direkt.jooq.tables.records.AssetVariantRecord
import io.direkt.domain.variant.Attributes
import io.direkt.domain.variant.LQIPs
import io.direkt.domain.variant.Transformation
import io.direkt.infrastructure.datastore.postgres.ImageVariantAttributes
import io.direkt.infrastructure.datastore.postgres.ImageVariantTransformation
import io.direkt.infrastructure.datastore.postgres.getNonNull
import io.direkt.infrastructure.http.serialization.format
import java.time.LocalDateTime

data class AssetVariant(
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val isOriginalVariant: Boolean,
    val attributes: Attributes,
    val transformation: Transformation,
    val transformationKey: Long,
    val lqip: LQIPs,
    val createdAt: LocalDateTime,
) {
    companion object Factory {
        fun from(record: AssetVariantRecord): AssetVariant? {
            if (record.get(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.ID) == null) {
                return null
            }
            return AssetVariant(
                objectStoreBucket = record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.OBJECT_STORE_BUCKET),
                objectStoreKey = record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.OBJECT_STORE_KEY),
                isOriginalVariant = record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.ORIGINAL_VARIANT),
                attributes =
                    format
                        .decodeFromString<ImageVariantAttributes>(
                            record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.ATTRIBUTES).data(),
                        ).toAttributes(),
                transformation =
                    format
                        .decodeFromString<ImageVariantTransformation>(
                            record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.TRANSFORMATION).data(),
                        ).toTransformation(),
                transformationKey = record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.TRANSFORMATION_KEY),
                lqip = format.decodeFromString(record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.LQIP).data()),
                createdAt = record.getNonNull(direkt.jooq.tables.AssetVariant.ASSET_VARIANT.CREATED_AT),
            )
        }
    }
}
