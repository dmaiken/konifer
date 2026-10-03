package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.selector.Order
import kotlinx.coroutines.runBlocking

/** Blocking asset selection relative to a path, label filter, and ordering. */
class BlockingRelativeAssetSelection internal constructor(
    private val selection: RelativeAssetSelection,
) : BlockingAssetSelection(selection) {
    /** Replaces the current label filter with a snapshot of [labels]. */
    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    /** Returns an independent selection that uses [order]. */
    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    /** Fetches metadata for matching entries using the default result limit. */
    fun fetchEntries(): KoniferResult<AssetEntriesResponse> = runBlocking { selection.fetchEntries() }

    /** Fetches metadata for at most [limit] matching entries. */
    fun fetchEntries(limit: Int): KoniferResult<AssetEntriesResponse> = runBlocking { selection.fetchEntries(limit) }

    /** Deletes at most [limit] matching entries in the selected order. */
    fun deleteFirst(limit: Int): KoniferResult<Unit> = runBlocking { selection.deleteFirst(limit) }

    /** Deletes matching assets at this path and its descendant paths. */
    fun deleteRecursively(): KoniferResult<Unit> = runBlocking { selection.deleteRecursively() }
}
