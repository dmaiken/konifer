package io.konifer.clientV2

import io.konifer.clientV2.assets.AbsoluteAssetSelection
import io.konifer.clientV2.assets.AssetAtPath
import io.konifer.clientV2.assets.AssetSelection
import io.konifer.clientV2.assets.RelativeAssetSelection
import io.konifer.clientV2.assets.VariantSelection
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.store.BlockingBlankAssetAtPath
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
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
    fun variant(transformation: RequestedTransformation): BlockingVariantSelection =
        BlockingVariantSelection(selection.variant(transformation))

    fun originalVariant(): BlockingVariantSelection = BlockingVariantSelection(selection.originalVariant())

    fun info(): KoniferV2Result<AssetResponse> = runBlocking { selection.info() }

    fun delete(): KoniferV2Result<Unit> = runBlocking { selection.delete() }
}

class BlockingAssetAtPath internal constructor(
    private val selection: AssetAtPath,
) : BlockingAssetSelection(selection) {
    fun entry(entryId: Long): BlockingAbsoluteAssetSelection = BlockingAbsoluteAssetSelection(selection.entry(entryId))

    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    fun entries(): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.entries() }

    fun entries(limit: Int): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.entries(limit) }

    fun delete(limit: Int): KoniferV2Result<Unit> = runBlocking { selection.delete(limit) }

    fun deleteRecursively(): KoniferV2Result<Unit> = runBlocking { selection.deleteRecursively() }

    /** Java-friendly name for the underlying `new()` operation. */
    fun newAsset(): BlockingBlankAssetAtPath = BlockingBlankAssetAtPath(selection.newAsset())
}

class BlockingRelativeAssetSelection internal constructor(
    private val selection: RelativeAssetSelection,
) : BlockingAssetSelection(selection) {
    fun matchingLabels(labels: Map<String, String>): BlockingRelativeAssetSelection =
        BlockingRelativeAssetSelection(selection.matchingLabels(labels))

    fun orderBy(order: Order): BlockingRelativeAssetSelection = BlockingRelativeAssetSelection(selection.orderBy(order))

    fun entries(): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.entries() }

    fun entries(limit: Int): KoniferV2Result<AssetEntriesResponse> = runBlocking { selection.entries(limit) }

    fun delete(limit: Int): KoniferV2Result<Unit> = runBlocking { selection.delete(limit) }

    fun deleteRecursively(): KoniferV2Result<Unit> = runBlocking { selection.deleteRecursively() }
}

class BlockingAbsoluteAssetSelection internal constructor(
    private val selection: AbsoluteAssetSelection,
) : BlockingAssetSelection(selection) {
    fun update(request: StoreAssetRequest): KoniferV2Result<AssetResponse> = runBlocking { selection.update(request) }
}

class BlockingVariantSelection internal constructor(
    private val selection: VariantSelection,
) {
    fun contentBytes(): KoniferV2Result<ByteArray> = runBlocking { selection.contentBytes() }

    fun contentBytes(delivery: ContentDelivery): KoniferV2Result<ByteArray> = runBlocking { selection.contentBytes(delivery) }

    fun writeContentTo(output: OutputStream): KoniferV2Result<Unit> = writeContentTo(output, ContentDelivery.THROUGH_KONIFER)

    fun writeContentTo(
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
                val result = selection.writeContentTo(channel, delivery)
                if (result is KoniferV2Result.Success) copy.await() else copy.cancel()
                result
            } finally {
                channel.cancel(null)
                copy.cancel()
            }
        }

    fun link(): KoniferV2Result<AssetLinkResponse> = runBlocking { selection.link() }
}
