package asset.model

import asset.Asset
import asset.variant.AssetVariant
import org.jooq.Record
import tessa.jooq.tables.records.AssetTreeRecord
import tessa.jooq.tables.records.AssetVariantRecord

data class AssetAndVariants(
    val asset: Asset,
    val variants: List<AssetVariant>,
) {
    companion object Factory {
        fun from(records: List<Record>): AssetAndVariants? {
            if (records.isEmpty()) {
                return null
            }
            val assetsToVariants = mutableMapOf<Asset, MutableList<AssetVariant>>()
            records.forEach { record ->
                if (assetsToVariants.size > 1) {
                    throw IllegalArgumentException("Multiple assets in record set")
                }
                val asset = Asset.from(record)
                val variants = assetsToVariants.computeIfAbsent(asset) { mutableListOf() }
                AssetVariant.from(record)?.let {
                    variants.add(it)
                }
            }
            val results =
                assetsToVariants.map { (asset, variants) ->
                    AssetAndVariants(
                        asset = asset,
                        variants = variants,
                    )
                }

            return results.first()
        }

        fun from(
            asset: AssetTreeRecord,
            variant: AssetVariantRecord,
        ): AssetAndVariants {
            return AssetAndVariants(
                asset = Asset.from(asset),
                variants = listOfNotNull(AssetVariant.from(variant)),
            )
        }
    }

    fun toResponse(): AssetResponse =
        AssetResponse(
            `class` = AssetClass.IMAGE,
            alt = asset.alt,
            entryId = asset.entryId,
            variants =
                variants.map { variant ->
                    AssetVariantResponse(
                        bucket = variant.objectStoreBucket,
                        storeKey = variant.objectStoreKey,
                        imageAttributes =
                            ImageAttributeResponse(
                                height = variant.attributes.height,
                                width = variant.attributes.width,
                                mimeType = variant.attributes.mimeType,
                            ),
                        lqip =
                            LQIPResponse(
                                thumbhash = variant.lqip.thumbhash,
                                blurhash = variant.lqip.blurhash,
                            ),
                    )
                },
            createdAt = asset.createdAt,
        )

    fun getOriginalVariant(): AssetVariant = variants.first { it.isOriginalVariant }
}
