package io.konifer.clientV2.request

import io.konifer.clientV2.VariantSelection
import io.konifer.clientV2.model.KoniferV2Result
import io.konifer.clientV2.model.RequestedTransformation
import io.konifer.common.http.AssetResponse

sealed interface AssetSelection {
    fun variant(requestedTransformation: RequestedTransformation): VariantSelection

    suspend fun info(): KoniferV2Result<AssetResponse>

    suspend fun delete(): KoniferV2Result<Unit>
}
