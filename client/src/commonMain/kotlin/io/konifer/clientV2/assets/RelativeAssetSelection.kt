package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.delete.AssetDeleteTarget
import io.konifer.clientV2.assets.delete.deleteAsset
import io.konifer.clientV2.assets.fetch.OrderBy
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.fetch.fetchAssetEntries
import io.konifer.clientV2.assets.fetch.fetchAssetInfo
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.DEFAULT_ENTRIES_LIMIT
import io.konifer.common.selector.Order

/**
 * Selects asset entries relative to a path using label filters and server-defined ordering.
 *
 * Operations that require one asset use the first matching entry in [orderBy].
 */
class RelativeAssetSelection internal constructor(
    private val infra: RequestInfrastructure,
    internal val path: String,
    labels: Map<String, String> = emptyMap(),
    internal val orderBy: Order = Order.NEW,
) : AssetSelection {
    internal val labels: Map<String, String> = labels.toMap()

    /** Replaces the current label filter with a snapshot of [labels]. */
    fun matchingLabels(labels: Map<String, String>): RelativeAssetSelection =
        RelativeAssetSelection(
            infra = infra,
            path = path,
            labels = labels,
            orderBy = orderBy,
        )

    /** Returns an independent selection that uses [order]. */
    fun orderBy(order: Order): RelativeAssetSelection =
        RelativeAssetSelection(
            infra = infra,
            path = path,
            labels = labels,
            orderBy = order,
        )

    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        VariantSelection(
            asset = this,
            infra = infra,
            requestedTransformation = requestedTransformation,
        )

    override suspend fun fetchInfo(): KoniferV2Result<AssetResponse> =
        fetchAssetInfo(
            infra = infra,
            path = path,
            selector = OrderBy(orderBy),
            labels = labels,
        )

    override suspend fun deleteFirst(): KoniferV2Result<Unit> = deleteFirst(limit = 1)

    /** Deletes at most [limit] matching entries in the selected order. */
    suspend fun deleteFirst(limit: Int): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.AtPath(orderBy, labels, limit),
        )

    /** Deletes matching assets at this path and its descendant paths. */
    suspend fun deleteRecursively(): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.Recursive(labels),
        )

    /** Fetches metadata for matching entries using [DEFAULT_ENTRIES_LIMIT]. */
    suspend fun fetchEntries(): KoniferV2Result<AssetEntriesResponse> = fetchEntries(limit = DEFAULT_ENTRIES_LIMIT)

    /**
     * Fetches metadata for at most [limit] matching entries in the selected order.
     *
     * @throws IllegalArgumentException if [limit] is not positive.
     */
    suspend fun fetchEntries(limit: Int): KoniferV2Result<AssetEntriesResponse> {
        require(limit > 0) { "Limit must be positive" }
        return fetchAssetEntries(
            infra = infra,
            path = path,
            selector = OrderBy(orderBy),
            labels = labels,
            limit = limit,
        )
    }
}
