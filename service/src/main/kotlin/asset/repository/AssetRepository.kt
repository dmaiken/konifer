package asset.repository

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import image.model.RequestedImageAttributes
import io.asset.handler.StoreAssetVariantDto

interface AssetRepository {
    suspend fun store(asset: StoreAssetDto): AssetAndVariants

    suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants

    suspend fun fetchByPath(
        treePath: String,
        entryId: Long?,
        requestedImageAttributes: RequestedImageAttributes?,
    ): AssetAndVariants?

    suspend fun fetchAllByPath(treePath: String): List<AssetAndVariants>

    suspend fun deleteAssetByPath(
        treePath: String,
        entryId: Long? = null,
    ): List<VariantBucketAndKey>

    suspend fun deleteAssetsByPath(
        treePath: String,
        recursive: Boolean,
    ): List<VariantBucketAndKey>
}
