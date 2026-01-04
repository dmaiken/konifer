package io.direkt.domain.ports

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetData
import io.direkt.domain.variant.Transformation
import io.direkt.domain.variant.Variant
import io.direkt.domain.variant.VariantBucketAndKey
import io.direkt.service.context.OrderBy

interface AssetRepository {
    suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted

    suspend fun markReady(asset: Asset.Ready)

    suspend fun markUploaded(variant: Variant.Ready)

    suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending

    suspend fun fetchForUpdate(
        path: String,
        entryId: Long,
    ): Asset?

    /**
     * Fetch the asset by path. If the asset itself does not exist, null is returned.
     * If the asset exists but has no variants that match the [transformation], then
     * [AssetData] will contain an empty [AssetData.variants].
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
        includeOnlyReady: Boolean = true,
    ): AssetData?

    /**
     * Fetch assets at the specific [path].
     *
     * @param transformation null means fetch all variants and any transformations that has [Transformation.originalVariant] == true
     * will fetch the original variant regardless of the rest of the transformation parameters.
     * @param orderBy sorts the assets at the [path] before applying the [limit]
     * @param labels filters assets at the path before sorting and applying the [limit]
     * @param limit the maximum amount of assets to return. -1 means no limit.
     */
    suspend fun fetchAllByPath(
        path: String,
        transformation: Transformation?,
        orderBy: OrderBy = OrderBy.CREATED,
        labels: Map<String, String> = emptyMap(),
        limit: Int,
    ): List<AssetData>

    suspend fun deleteByPath(
        path: String,
        entryId: Long,
    ): List<VariantBucketAndKey>

    suspend fun deleteAllByPath(
        path: String,
        orderBy: OrderBy = OrderBy.CREATED,
        limit: Int,
    ): List<VariantBucketAndKey>

    suspend fun deleteRecursivelyByPath(path: String): List<VariantBucketAndKey>

    /**
     * @throws IllegalStateException if asset cannot be found with the given path and entryId
     */
    suspend fun update(asset: Asset): Asset
}
