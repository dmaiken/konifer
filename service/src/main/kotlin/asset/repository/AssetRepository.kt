package asset.repository

import asset.handler.StoreAssetDto
import asset.model.AssetAndVariants
import asset.model.VariantBucketAndKey
import asset.store.PersistResult
import image.model.ImageAttributes
import image.model.RequestedImageAttributes

interface AssetRepository {
    suspend fun store(asset: StoreAssetDto): AssetAndVariants

    suspend fun storeVariant(
        treePath: String,
        entryId: Long,
        persistResult: PersistResult,
        imageAttributes: ImageAttributes,
    ): AssetAndVariants

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
