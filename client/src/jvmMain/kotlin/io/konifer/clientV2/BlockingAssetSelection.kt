package io.konifer.clientV2

import io.konifer.clientV2.assets.AbsoluteAssetSelection
import io.konifer.clientV2.assets.AssetAtPath
import io.konifer.clientV2.assets.AssetSelection
import io.konifer.clientV2.assets.RelativeAssetSelection
import io.konifer.clientV2.assets.VariantSelection
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.store.BlockingBlankAssetAtPath
import io.konifer.clientV2.assets.update.BlockingAssetUpdateAtPath
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.selector.Order
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.OutputStream

open class BlockingAssetSelection internal constructor(
    private val selection: AssetSelection,
) {
    fun variant(requestedTransformation: RequestedTransformation): BlockingVariantSelection =
        BlockingVariantSelection(selection.variant(requestedTransformation))

    fun originalVariant(): BlockingVariantSelection = BlockingVariantSelection(selection.originalVariant())

    fun fetchInfo(): KoniferV2Result<AssetResponse> = runBlocking { selection.fetchInfo() }

    fun deleteFirst(): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst() }
}

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

class BlockingAbsoluteAssetSelection internal constructor(
    selection: AbsoluteAssetSelection,
) : BlockingAssetSelection(selection)

class BlockingVariantSelection internal constructor(
    private val selection: VariantSelection,
) {
    fun fetchContentBytes(): KoniferV2Result<ByteArray> = runBlocking { selection.fetchContentBytes() }

    fun fetchContentBytes(delivery: ContentDelivery): KoniferV2Result<ByteArray> = runBlocking { selection.fetchContentBytes(delivery) }

    fun fetchAndWriteContentTo(output: OutputStream): KoniferV2Result<Unit> =
        fetchAndWriteContentTo(output, ContentDelivery.THROUGH_KONIFER)

    fun fetchAndWriteContentTo(
        output: OutputStream,
        delivery: ContentDelivery,
    ): KoniferV2Result<Unit> =
        runBlocking {
            val channel = ByteChannel()
            val copy =
                async(Dispatchers.IO) {
                    try {
                        channel.copyTo(output)
                    } catch (failure: Throwable) {
                        channel.cancel(failure)
                        throw failure
                    }
                }
            try {
                val result = selection.fetchAndWriteContentTo(channel, delivery)
                if (result is KoniferV2Result.Success) copy.await() else copy.cancel()
                result
            } finally {
                channel.cancel(null)
                copy.cancel()
            }
        }

    fun fetchLink(): KoniferV2Result<AssetLinkResponse> = runBlocking { selection.fetchLink() }
}
