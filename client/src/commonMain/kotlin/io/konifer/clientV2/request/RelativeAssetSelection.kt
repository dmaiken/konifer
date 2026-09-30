package io.konifer.clientV2.request

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.VariantSelection
import io.konifer.clientV2.internal.AssetDeleteTarget
import io.konifer.clientV2.internal.deleteAsset
import io.konifer.clientV2.internal.fetchAssetInfo
import io.konifer.clientV2.model.KoniferV2Result
import io.konifer.clientV2.model.OrderBy
import io.konifer.clientV2.model.RequestedTransformation
import io.konifer.common.http.AssetResponse
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

    suspend fun deleteRecursively(): KoniferV2Result<Unit> = deleteAsset(infra, path, AssetDeleteTarget.Recursive(labels))

    suspend fun info(limit: Int): KoniferV2Result<List<AssetResponse>> {
        require(limit > 0) { "Limit must be positive" }
        if (limit == 1) {
            return when (val result = info()) {
                is KoniferV2Result.Success -> KoniferV2Result.Success(listOf(result.value))
                is KoniferV2Result.Failure -> result
            }
        }
        return fetchAssetInfo(
            infra = infra,
            path = path,
            selector = OrderBy(orderBy),
            labels = labels,
            limit = limit,
        )
    }
}
