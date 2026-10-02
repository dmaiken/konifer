package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.assets.delete.AssetDeleteTarget
import io.konifer.clientV2.assets.delete.deleteAsset
import io.konifer.clientV2.assets.fetch.OrderBy
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.fetch.fetchAssetEntries
import io.konifer.clientV2.assets.fetch.fetchAssetInfo
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.DEFAULT_ENTRIES_LIMIT
import io.konifer.common.selector.Order

class RelativeAssetSelection internal constructor(
    private val infra: RequestInfrastructure,
    internal val path: String,
    labels: Map<String, String> = emptyMap(),
    internal val orderBy: Order = Order.NEW,
) : AssetSelection {
    internal val labels = labels.toMap()

    fun matchingLabels(labels: Map<String, String>): RelativeAssetSelection =
        RelativeAssetSelection(
            infra = infra,
            path = path,
            labels = labels,
            orderBy = orderBy,
        )

    fun orderBy(orderBy: Order): RelativeAssetSelection =
        RelativeAssetSelection(
            infra = infra,
            path = path,
            labels = labels,
            orderBy = orderBy,
        )

    override fun variant(requestedTransformation: RequestedTransformation) =
        VariantSelection(
            asset = this,
            infra = infra,
            requestedTransformation = requestedTransformation,
        )

    override suspend fun info(): KoniferV2Result<AssetResponse> =
        fetchAssetInfo(
            infra = infra,
            path = path,
            selector = OrderBy(orderBy),
            labels = labels,
        )

    override suspend fun delete(): KoniferV2Result<Unit> = delete(limit = 1)

    suspend fun delete(limit: Int): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.AtPath(orderBy, labels, limit),
        )

    suspend fun deleteRecursively(): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.Recursive(labels),
        )

    suspend fun entries(): KoniferV2Result<AssetEntriesResponse> = entries(limit = DEFAULT_ENTRIES_LIMIT)

    suspend fun entries(limit: Int): KoniferV2Result<AssetEntriesResponse> {
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
