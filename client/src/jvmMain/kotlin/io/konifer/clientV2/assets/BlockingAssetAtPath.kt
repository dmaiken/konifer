package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.store.BlockingBlankAssetAtPath
import io.konifer.clientV2.assets.update.BlockingAssetUpdateAtPath
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.Order
import kotlinx.coroutines.runBlocking

/** Blocking entry point for asset operations at a Konifer path. */
class BlockingAssetAtPath internal constructor(
    private val selection: AssetAtPath,
) : BlockingAssetSelection(selection) {
    /** Selects the entry identified by [entryId]. */
    fun entry(entryId: Long): BlockingAbsoluteAssetSelection = BlockingAbsoluteAssetSelection(selection.entry(entryId))

    /** Restricts the selection to entries matching [labels]. */
    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    /** Selects entries using [order]. */
    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    /** Fetches metadata for entries at this path using the default result limit. */
    fun fetchEntries(): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries() }

    /** Fetches metadata for at most [limit] entries at this path. */
    fun fetchEntries(limit: Int): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries(limit) }

    /** Deletes at most [limit] entries at this path, in the selected order. */
    fun deleteFirst(limit: Int): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst(limit) }

    /** Deletes assets at this path and its descendant paths. */
    fun deleteRecursively(): KoniferV2Result<Unit> = runBlocking { selection.deleteRecursively() }

    /** Starts an immutable request to store a new image at this path. */
    fun newAsset(): BlockingBlankAssetAtPath = BlockingBlankAssetAtPath(selection.newAsset())

    /** Starts a metadata update for [current]'s entry, preserving a snapshot of its editable fields. */
    fun updateAsset(current: AssetResponse): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.updateAsset(current))
}
