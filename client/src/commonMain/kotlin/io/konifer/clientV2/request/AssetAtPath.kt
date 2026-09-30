package io.konifer.clientV2.request

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.VariantSelection
import io.konifer.clientV2.model.KoniferV2Result
import io.konifer.clientV2.model.RequestedTransformation
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.Order

class AssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
) : AssetSelection {
    private val relativeSelection = RelativeAssetSelection(infra = infra, path = path)

    fun entry(entryId: Long): AbsoluteAssetSelection =
        AbsoluteAssetSelection(
            infra = infra,
            path = path,
            entryId = entryId,
        )

    fun matchingLabels(labels: Map<String, String>): RelativeAssetSelection = relativeSelection.matchingLabels(labels)

    fun orderBy(orderBy: Order): RelativeAssetSelection = relativeSelection.orderBy(orderBy)

    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        relativeSelection.variant(requestedTransformation)

    override suspend fun info(): KoniferV2Result<AssetResponse> = relativeSelection.info()

    suspend fun info(limit: Int): KoniferV2Result<List<AssetResponse>> = relativeSelection.info(limit)

    override suspend fun delete(): KoniferV2Result<Unit> = relativeSelection.delete()

    suspend fun delete(limit: Int): KoniferV2Result<Unit> = relativeSelection.delete(limit)

    suspend fun deleteRecursively(): KoniferV2Result<Unit> = relativeSelection.deleteRecursively()
}
