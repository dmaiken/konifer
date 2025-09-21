package asset.variant

import asset.repository.getNonNull
import image.model.LQIPs
import io.asset.variant.ImageVariantAttributes
import kotlinx.serialization.json.Json
import org.jooq.Record
import tessa.jooq.tables.AssetVariant.Companion.ASSET_VARIANT
import java.time.LocalDateTime

data class AssetVariant(
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val isOriginalVariant: Boolean,
    val attributes: ImageVariantAttributes,
    val transformations: ImageVariantTransformation,
    val transformationKey: Long,
    val lqip: LQIPs,
    val createdAt: LocalDateTime,
) {
    companion object Factory {
        fun from(record: Record): AssetVariant? {
            if (record.get(ASSET_VARIANT.ID) == null) {
                return null
            }
            return AssetVariant(
                objectStoreBucket = record.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                objectStoreKey = record.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                isOriginalVariant = record.getNonNull(ASSET_VARIANT.ORIGINAL_VARIANT),
                attributes = Json.decodeFromString(record.getNonNull(ASSET_VARIANT.ATTRIBUTES).data()),
                transformations = Json.decodeFromString(record.getNonNull(ASSET_VARIANT.TRANSFORMATIONS).data()),
                transformationKey = record.getNonNull(ASSET_VARIANT.TRANSFORMATION_KEY),
                lqip = Json.decodeFromString(record.getNonNull(ASSET_VARIANT.LQIP).data()),
                createdAt = record.getNonNull(ASSET_VARIANT.CREATED_AT),
            )
        }
    }
}
