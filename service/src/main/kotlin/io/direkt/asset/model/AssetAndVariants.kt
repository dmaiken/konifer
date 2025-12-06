package io.direkt.asset.model

import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetTreeRecord
import direkt.jooq.tables.records.AssetVariantRecord
import io.direkt.domain.asset.AssetClass
import io.direkt.infrastructure.http.AssetResponse
import io.direkt.infrastructure.http.AssetVariantResponse
import io.direkt.infrastructure.http.ImageAttributeResponse
import io.direkt.infrastructure.http.LQIPResponse

data class AssetAndVariants(
    val asset: Asset,
    val variants: List<AssetVariant>,
) {
    companion object Factory {
        fun from(
            asset: AssetTreeRecord,
            variant: AssetVariantRecord,
            labels: Map<String, String>,
            tags: Set<String>,
        ): AssetAndVariants =
            AssetAndVariants(
                asset = Asset.from(asset, labels, tags),
                variants = listOfNotNull(AssetVariant.from(variant)),
            )

        fun from(
            asset: AssetTreeRecord,
            variant: List<AssetVariantRecord>,
            labels: List<AssetLabelRecord>,
            tags: List<AssetTagRecord>,
        ): AssetAndVariants =
            AssetAndVariants(
                asset =
                    Asset.from(
                        record = asset,
                        labels = labels.associate { it.labelKey!! to it.labelValue!! },
                        tags = tags.mapNotNull { it.tagValue }.toSet(),
                    ),
                variants = variant.mapNotNull { AssetVariant.from(it) },
            )
    }

    fun toResponse(): AssetResponse =
        AssetResponse(
            `class` = AssetClass.IMAGE,
            alt = asset.alt,
            entryId = asset.entryId,
            labels = asset.labels,
            tags = asset.tags,
            source = asset.source,
            sourceUrl = asset.sourceUrl,
            variants =
                variants.map { variant ->
                    AssetVariantResponse(
                        bucket = variant.objectStoreBucket,
                        storeKey = variant.objectStoreKey,
                        attributes =
                            ImageAttributeResponse(
                                height = variant.attributes.height,
                                width = variant.attributes.width,
                                mimeType = variant.attributes.format.mimeType,
                                pageCount = variant.attributes.pageCount,
                                loop = variant.attributes.loop,
                            ),
                        lqip =
                            LQIPResponse(
                                thumbhash = variant.lqip.thumbhash,
                                blurhash = variant.lqip.blurhash,
                            ),
                    )
                },
            createdAt = asset.createdAt,
            modifiedAt = asset.modifiedAt,
        )

    fun getOriginalVariant(): AssetVariant = variants.first { it.isOriginalVariant }
}
