package io.direkt.domain.ports

import io.direkt.asset.handler.dto.StoreAssetVariantDto
import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.asset.model.AssetAndVariants
import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantBucketAndKey
import io.direkt.service.context.OrderBy

interface AssetRepository {
    suspend fun storeNew(asset: Asset): Asset.PendingPersisted

    suspend fun markReady(asset: Asset)

    suspend fun storeVariant(variant: Variant): Variant

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
        orderBy: OrderBy = OrderBy.CREATED,
        labels: Map<String, String> = emptyMap(),
    ): AssetData?

    suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy = OrderBy.CREATED,
        labels: Map<String, String> = emptyMap(),
        limit: Int,
    ): List<AssetData>

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
    suspend fun update(asset: Asset): Asset
}
