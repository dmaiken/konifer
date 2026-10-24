package io.konifer.domain.ports

import io.konifer.common.selector.Order
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.projection.AssetData
import io.konifer.domain.asset.projection.AssetDeliveryProjection
import io.konifer.domain.variant.Variant
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantSelection
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.domain.variant.retention.CacheProperties

interface AssetRepository {
    /** Fetch uploaded variants of an already resolved asset, newest first. Returns an empty list if none exist. */
    suspend fun fetchVariantsForDelivery(
        assetId: AssetId,
        selection: VariantSelection = VariantSelection.ALL,
    ): List<VariantDeliveryProjection>

    suspend fun storeNew(asset: Asset.Pending): Asset.PendingPersisted

    suspend fun markReady(asset: Asset.Ready)

    suspend fun markUploaded(
        variant: Variant.Ready,
        cacheProperties: CacheProperties,
    ): VariantData

    suspend fun storeNewVariant(variant: Variant.Pending): Variant.Pending

    suspend fun fetchForUpdate(
        path: String,
        entryId: Long,
    ): Asset?

    suspend fun fetchForDelivery(
        path: String,
        entryId: Long?,
        selection: VariantSelection = VariantSelection.ALL,
        order: Order = Order.NEW,
        labels: Map<String, String> = emptyMap(),
    ): AssetDeliveryProjection?

    /**
     * Fetch the asset by path. If the asset itself does not exist, null is returned.
     * If the asset exists but has no eligible variants for the [selection], then
     * [AssetData] will contain an empty [AssetData.variants].
     *
     * @param path the url path
     * @param entryId the entryId, can be null
     * @param selection which variants to include; defaults to all eligible variants
     */
    suspend fun fetchByPath(
        path: String,
        entryId: Long?,
        selection: VariantSelection = VariantSelection.ALL,
        order: Order = Order.NEW,
        labels: Map<String, String> = emptyMap(),
        includeOnlyReady: Boolean = true,
    ): AssetData?

    /**
     * Fetch assets at the specific [path].
     *
     * @param selection which variants to include; defaults to all eligible variants
     *
     * @param labels filters assets at the path before sorting and applying the [limit]
     * @param order sorts the assets at the [path] before applying the [limit]
     * @param limit the maximum amount of assets to return. -1 means no limit.
     */
    suspend fun fetchAllByPath(
        path: String,
        selection: VariantSelection = VariantSelection.ALL,
        labels: Map<String, String> = emptyMap(),
        order: Order = Order.NEW,
        limit: Int,
    ): List<AssetData>

    suspend fun deleteByPath(
        path: String,
        entryId: Long,
    )

    suspend fun deleteAllByPath(
        path: String,
        labels: Map<String, String> = emptyMap(),
        order: Order = Order.NEW,
        limit: Int,
    )

    suspend fun deleteRecursivelyByPath(
        path: String,
        labels: Map<String, String> = emptyMap(),
    )

    suspend fun deleteByAssetId(assetId: AssetId)

    /**
     * @throws IllegalStateException if asset cannot be found with the given path and entryId
     */
    suspend fun update(asset: Asset.Ready): Asset
}
