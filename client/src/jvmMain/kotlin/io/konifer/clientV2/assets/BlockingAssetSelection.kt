package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse
import kotlinx.coroutines.runBlocking

/** Blocking counterpart to [AssetSelection]. */
open class BlockingAssetSelection internal constructor(
    private val selection: AssetSelection,
) {
    /** Selects transformed content for this asset selection. */
    fun variant(requestedTransformation: RequestedTransformation): BlockingVariantSelection =
        BlockingVariantSelection(selection.variant(requestedTransformation))

    /** Selects the stored image without requesting transformations. */
    fun originalVariant(): BlockingVariantSelection = BlockingVariantSelection(selection.originalVariant())

    /** Fetches metadata for the selected asset entry. */
    fun fetchInfo(): KoniferV2Result<AssetResponse> = runBlocking { selection.fetchInfo() }

    /** Deletes the selected asset entry. */
    fun deleteFirst(): KoniferV2Result<Unit> = runBlocking { selection.deleteFirst() }
}
