package io.direkt.asset.model

import direkt.jooq.tables.records.AssetVariantRecord
import io.direkt.domain.image.Attributes
import io.direkt.domain.image.LQIPs
import io.direkt.domain.image.Transformation
import io.direkt.infrastructure.database.postgres.ImageVariantAttributes
import io.direkt.infrastructure.database.postgres.ImageVariantTransformation
import io.direkt.infrastructure.database.postgres.getNonNull
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
            if (record.get(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.ID) == null) {
                return null
            }
            return AssetVariant(
                objectStoreBucket = record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.OBJECT_STORE_BUCKET),
                objectStoreKey = record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.OBJECT_STORE_KEY),
                isOriginalVariant = record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.ORIGINAL_VARIANT),
                attributes =
                    format
                        .decodeFromString<ImageVariantAttributes>(
                            record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.ATTRIBUTES).data(),
                        ).toAttributes(),
                transformation =
                    format
                        .decodeFromString<ImageVariantTransformation>(
                            record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.TRANSFORMATION).data(),
                        ).toTransformation(),
                transformationKey = record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.TRANSFORMATION_KEY),
                lqip = format.decodeFromString(record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.LQIP).data()),
                createdAt = record.getNonNull(direkt.jooq.tables.AssetVariant.Companion.ASSET_VARIANT.CREATED_AT),
            )
        }
    }
}