package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.RequestedTransformation
import io.konifer.client.assets.store.BlankAssetAtPath
import io.konifer.client.assets.update.AssetUpdateAtPath
import io.konifer.client.internal.RequestInfrastructure
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.selector.Order

/**
 * Entry point for asset operations at a Konifer path.
 *
 * An operation that requires one asset uses the newest entry unless [orderBy], [matchingLabels],
 * or [entry] refines the selection.
 */
class AssetAtPath internal constructor(
    private val infra: RequestInfrastructure,
    private val path: String,
) : AssetSelection {
    private val relativeSelection: RelativeAssetSelection = RelativeAssetSelection(infra = infra, path = path)

    /** Selects the entry identified by [entryId]. */
    fun entry(entryId: Long): AbsoluteAssetSelection =
        AbsoluteAssetSelection(
            infra = infra,
            path = path,
            entryId = entryId,
        )

    /** Restricts the selection to entries matching [labels]. */
    fun matchingLabels(labels: Map<String, String>): RelativeAssetSelection = relativeSelection.matchingLabels(labels)

    /** Selects entries using [order]. */
    fun orderBy(order: Order): RelativeAssetSelection = relativeSelection.orderBy(order)

    override fun variant(requestedTransformation: RequestedTransformation): VariantSelection =
        relativeSelection.variant(requestedTransformation)

    override suspend fun fetchInfo(): KoniferResult<AssetResponse> = relativeSelection.fetchInfo()

    /** Fetches metadata for entries at this path using the default result limit. */
    suspend fun fetchEntries(): KoniferResult<AssetEntriesResponse> = relativeSelection.fetchEntries()

    /**
     * Fetches metadata for at most [limit] entries at this path.
     *
     * @throws IllegalArgumentException if [limit] is not positive.
     */
    suspend fun fetchEntries(limit: Int): KoniferResult<AssetEntriesResponse> = relativeSelection.fetchEntries(limit)

    override suspend fun deleteFirst(): KoniferResult<Unit> = relativeSelection.deleteFirst()

    /** Deletes at most [limit] entries at this path, in the selected order. */
    suspend fun deleteFirst(limit: Int): KoniferResult<Unit> = relativeSelection.deleteFirst(limit)

    /** Deletes assets at this path and its descendant paths. */
    suspend fun deleteRecursively(): KoniferResult<Unit> = relativeSelection.deleteRecursively()

    /** Starts an immutable request to store a new image at this path. */
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
