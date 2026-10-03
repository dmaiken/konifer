package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse
import kotlinx.coroutines.runBlocking

open class BlockingAssetSelection internal constructor(
    private val selection: AssetSelection,
) {
    fun variant(requestedTransformation: RequestedTransformation): BlockingVariantSelection =
        BlockingVariantSelection(selection.variant(requestedTransformation))

    fun originalVariant(): BlockingVariantSelection = BlockingVariantSelection(selection.originalVariant())

    fun fetchInfo(): KoniferV2Result<AssetResponse> = runBlocking { selection.fetchInfo() }

    fun deleteFirst(): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst() }
}
