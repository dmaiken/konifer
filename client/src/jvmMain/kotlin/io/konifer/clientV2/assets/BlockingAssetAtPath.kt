package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.store.BlockingBlankAssetAtPath
import io.konifer.clientV2.assets.update.BlockingAssetUpdateAtPath
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.Order
import kotlinx.coroutines.runBlocking

class BlockingAssetAtPath internal constructor(
    private val selection: AssetAtPath,
) : BlockingAssetSelection(selection) {
    fun entry(entryId: Long): BlockingAbsoluteAssetSelection = BlockingAbsoluteAssetSelection(selection.entry(entryId))

    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    fun fetchEntries(): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries() }

    fun fetchEntries(limit: Int): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries(limit) }

    fun deleteFirst(limit: Int): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst(limit) }

    fun deleteRecursively(): KoniferV2Result<Unit> = runBlocking { selection.deleteRecursively() }

    fun newAsset(): BlockingBlankAssetAtPath = BlockingBlankAssetAtPath(selection.newAsset())

    /** Starts a metadata update for [current]'s entry, preserving a snapshot of its editable fields. */
    fun updateAsset(current: AssetResponse): BlockingAssetUpdateAtPath = BlockingAssetUpdateAtPath(selection.updateAsset(current))
}
