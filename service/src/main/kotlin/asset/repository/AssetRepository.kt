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
        path: String,
        entryId: Long?,
        requestedImageAttributes: RequestedImageAttributes?,
    ): AssetAndVariants?

    suspend fun fetchAllByPath(
        path: String,
        requestedImageAttributes: RequestedImageAttributes?,
    ): List<AssetAndVariants>

    suspend fun deleteAssetByPath(
        path: String,
        entryId: Long? = null,
    ): List<VariantBucketAndKey>

    suspend fun deleteAssetsByPath(
        path: String,
        recursive: Boolean,
    ): List<VariantBucketAndKey>
}
