package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.selector.Order
import kotlinx.coroutines.runBlocking

class BlockingRelativeAssetSelection internal constructor(
    private val selection: RelativeAssetSelection,
) : BlockingAssetSelection(selection) {
    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    fun fetchEntries(): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries() }

    fun fetchEntries(limit: Int): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.fetchEntries(limit) }

    fun deleteFirst(limit: Int): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst(limit) }

    fun deleteRecursively(): KoniferV2Result<Unit> = runBlocking { selection.deleteRecursively() }
}
