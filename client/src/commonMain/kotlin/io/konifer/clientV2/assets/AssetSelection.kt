package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse

sealed interface AssetSelection {
    fun variant(requestedTransformation: RequestedTransformation): VariantSelection

    fun originalVariant(): VariantSelection = variant(RequestedTransformation.OriginalVariant)

    suspend fun fetchInfo(): KoniferV2Result<AssetResponse>

    suspend fun deleteFirst(): KoniferV2Result<Unit>
}
