package io.asset.repository

import io.asset.handler.StoreAssetDto
import io.asset.handler.StoreAssetVariantDto
import io.asset.handler.UpdateAssetDto
import io.asset.model.Asset
import io.asset.model.AssetAndVariants
import io.asset.model.VariantBucketAndKey
import io.image.model.Transformation

interface AssetRepository {
    suspend fun store(asset: StoreAssetDto): AssetAndVariants

    suspend fun storeVariant(variant: StoreAssetVariantDto): AssetAndVariants

    /**
     * Fetch the asset by path. If the asset itself does not exist, null is returned.
     * If the asset exists but has no variants that match the [transformation], then
     * [AssetAndVariants] will contain an empty [AssetAndVariants.variants].
     *
     * @param path the url path
     * @param entryId the entryId, can be null
     * @param transformation null means fetch all variants
     */
    suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        transformation: Transformation?,
        labels: Map<String, String> = emptyMap(),
    ): AssetAndVariants?

    suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        labels: Map<String, String> = emptyMap(),
    ): List<AssetAndVariants>

    suspend fun deleteAssetByPath(
        path: String,
        entryId: Long? = null,
    ): List<VariantBucketAndKey>

    suspend fun deleteAssetsByPath(
        path: String,
        recursive: Boolean,
    ): List<VariantBucketAndKey>

    /**
     * @throws IllegalStateException if asset cannot be found with the given path and entryId
     */
    suspend fun update(asset: UpdateAssetDto): AssetAndVariants
}
