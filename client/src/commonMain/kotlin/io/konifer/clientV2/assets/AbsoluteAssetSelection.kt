package io.konifer.clientV2.assets

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.assets.delete.AssetDeleteTarget
import io.konifer.clientV2.assets.delete.deleteAsset
import io.konifer.clientV2.assets.fetch.fetchAssetInfo
import io.konifer.clientV2.assets.update.updateAsset
import io.konifer.clientV2.assets.fetch.EntryId
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest

class AbsoluteAssetSelection internal constructor(
    private val infra: RequestInfrastructure,
    internal val path: String,
    val entryId: Long,
) : AssetSelection {
    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        VariantSelection(
            infra = infra,
            asset = this,
            requestedTransformation = requestedTransformation,
        )

    override suspend fun info(): KoniferV2Result<AssetResponse> =
        fetchAssetInfo(
            infra = infra,
            path = path,
            selector = EntryId(entryId),
        )

    override suspend fun delete(): KoniferV2Result<Unit> =
        deleteAsset(
            infra = infra,
            path = path,
            target = AssetDeleteTarget.Entry(entryId),
        )

    suspend fun update(request: StoreAssetRequest): KoniferV2Result<AssetResponse> = updateAsset(infra, path, entryId, request)
}
