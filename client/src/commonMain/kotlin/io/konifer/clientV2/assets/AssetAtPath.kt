package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.store.BlankAssetAtPath
import io.konifer.clientV2.assets.update.AssetUpdateAtPath
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
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

    fun orderBy(order: Order): RelativeAssetSelection = relativeSelection.orderBy(order)

    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        relativeSelection.variant(requestedTransformation)

    override suspend fun fetchInfo(): KoniferV2Result<AssetResponse> = relativeSelection.fetchInfo()

    suspend fun fetchEntries(): KoniferV2Result<AssetEntriesResponse> = relativeSelection.fetchEntries()

    suspend fun fetchEntries(limit: Int): KoniferV2Result<AssetEntriesResponse> = relativeSelection.fetchEntries(limit)

    override suspend fun deleteFirst(): KoniferV2Result<Unit> = relativeSelection.deleteFirst()

    suspend fun deleteFirst(limit: Int): KoniferV2Result<Unit> = relativeSelection.deleteFirst(limit)

    suspend fun deleteRecursively(): KoniferV2Result<Unit> = relativeSelection.deleteRecursively()

    fun newAsset(): BlankAssetAtPath = BlankAssetAtPath(infra = infra, path = path)

    /** Starts a metadata update for [current]'s entry, preserving a snapshot of its editable fields. */
    fun updateAsset(current: AssetResponse): AssetUpdateAtPath =
        AssetUpdateAtPath(
            infra = infra,
            path = path,
            entryId = current.entryId,
            request =
                StoreAssetRequest(
                    alt = current.alt,
                    labels = current.labels.toMap(),
                    tags = current.tags.toSet(),
                ),
        )
}
