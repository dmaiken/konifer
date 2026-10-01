package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.common.http.AssetResponse

sealed interface AssetSelection {
    fun variant(requestedTransformation: RequestedTransformation): VariantSelection

    suspend fun info(): KoniferV2Result<AssetResponse>

    suspend fun delete(): KoniferV2Result<Unit>
}
