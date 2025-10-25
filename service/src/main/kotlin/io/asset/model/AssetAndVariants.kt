package io.asset.model

import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTreeRecord
import direkt.jooq.tables.records.AssetVariantRecord
import io.asset.variant.AssetVariant

data class AssetAndVariants(
    val asset: Asset,
    val variants: List<AssetVariant>,
) {
    companion object Factory {
        fun from(
            asset: AssetTreeRecord,
            variant: AssetVariantRecord,
            labels: Map<String, String>,
        ): AssetAndVariants {
            return AssetAndVariants(
                asset = Asset.from(asset, labels),
                variants = listOfNotNull(AssetVariant.from(variant)),
            )
        }

        fun from(
            asset: AssetTreeRecord,
            variant: List<AssetVariantRecord>,
            labels: List<AssetLabelRecord>,
        ): AssetAndVariants {
            return AssetAndVariants(
                asset = Asset.from(asset, labels.associate { it.labelKey!! to it.labelValue!! }),
                variants = variant.mapNotNull { AssetVariant.from(it) },
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
                                height = variant.transformation.height,
                                width = variant.transformation.width,
                                mimeType = variant.transformation.format.mimeType,
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
