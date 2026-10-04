package io.konifer.client.assets

import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse

/** Selects either a specific asset entry or the first entry matching path criteria. */
sealed interface AssetSelection {
    /** Selects transformed content for this asset selection. */
    fun variant(requestedTransformation: RequestedTransformation): VariantSelection

    /** Selects the stored image without requesting transformations. */
    fun originalVariant(): VariantSelection = variant(RequestedTransformation.OriginalVariant)

    /** Fetches metadata for the selected asset entry. */
    suspend fun fetchInfo(): KoniferResult<AssetResponse>

    /** Deletes the selected asset entry. */
    suspend fun deleteFirst(): KoniferResult<Unit>
}
