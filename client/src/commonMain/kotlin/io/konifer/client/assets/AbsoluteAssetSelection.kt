package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.client.assets.delete.AssetDeleteTarget
import io.konifer.client.assets.delete.deleteAsset
import io.konifer.client.assets.fetch.EntryId
import io.konifer.client.assets.fetch.RequestedTransformation
import io.konifer.client.assets.fetch.fetchAssetInfo
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.common.http.AssetResponse

/** Selects one asset entry by its path and entry ID. */
class AbsoluteAssetSelection internal constructor(
    private val infra: RequestInfrastructure,
    internal val path: String,
    internal val entryId: Long,
) : AssetSelection {
    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        VariantSelection(
            infra = infra,
            asset = this,
            requestedTransformation = requestedTransformation,
        )

    override suspend fun fetchInfo(): KoniferResult<AssetResponse> =
        fetchAssetInfo(
            infra = infra,
            path = path,
            selector = EntryId(entryId),
        )

    override suspend fun deleteFirst(): KoniferResult<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.Entry(entryId),
        )
}
