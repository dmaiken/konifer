package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse

/** Selects either a specific asset entry or the first entry matching path criteria. */
sealed interface AssetSelection {
    /** Selects transformed content for this asset selection. */
    fun variant(requestedTransformation: RequestedTransformation): VariantSelection

    /** Selects the stored image without requesting transformations. */
    fun originalVariant(): VariantSelection = variant(RequestedTransformation.OriginalVariant)

    /** Fetches metadata for the selected asset entry. */
    suspend fun fetchInfo(): KoniferV2Result<AssetResponse>

    /** Deletes the selected asset entry. */
    suspend fun deleteFirst(): KoniferV2Result<Unit>
}
