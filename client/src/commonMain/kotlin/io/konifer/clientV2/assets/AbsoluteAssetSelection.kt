package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.delete.AssetDeleteTarget
import io.konifer.clientV2.assets.delete.deleteAsset
import io.konifer.clientV2.assets.fetch.EntryId
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.fetch.fetchAssetInfo
import io.konifer.clientV2.internal.RequestInfrastructure
import io.konifer.common.http.AssetResponse

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

    override suspend fun fetchInfo(): KoniferV2Result<AssetResponse> =
        fetchAssetInfo(
            infra = infra,
            path = path,
            selector = EntryId(entryId),
        )

    override suspend fun deleteFirst(): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.Entry(entryId),
        )
}
