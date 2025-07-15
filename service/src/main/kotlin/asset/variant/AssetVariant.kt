package asset.variant

import asset.repository.getNonNull
import kotlinx.serialization.json.Json
import org.jooq.Record
import tessa.jooq.tables.AssetVariant.Companion.ASSET_VARIANT
import java.time.LocalDateTime

data class AssetVariant(
    val objectStoreBucket: String,
    val objectStoreKey: String,
    val attributes: ImageVariantAttributes,
    val isOriginalVariant: Boolean,
    val attributeKey: Long,
    val createdAt: LocalDateTime,
) {
    companion object Factory {
        fun from(record: Record): AssetVariant {
            return AssetVariant(
                objectStoreBucket = record.getNonNull(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                objectStoreKey = record.getNonNull(ASSET_VARIANT.OBJECT_STORE_KEY),
                attributes = Json.decodeFromString(record.getNonNull(ASSET_VARIANT.ATTRIBUTES).data()),
                isOriginalVariant = record.getNonNull(ASSET_VARIANT.ORIGINAL_VARIANT),
                attributeKey = record.getNonNull(ASSET_VARIANT.ATTRIBUTES_KEY),
                createdAt = record.getNonNull(ASSET_VARIANT.CREATED_AT),
            )
        }
    }
}
