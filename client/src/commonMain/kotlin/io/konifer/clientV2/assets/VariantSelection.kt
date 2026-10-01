package io.konifer.clientV2.assets

import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.fetch.fetchAssetContentBytes
import io.konifer.clientV2.assets.fetch.fetchAssetContentTo
import io.konifer.clientV2.assets.fetch.fetchAssetLink
import io.konifer.common.http.AssetLinkResponse
import io.ktor.utils.io.ByteWriteChannel

class VariantSelection internal constructor(
    private val infra: RequestInfrastructure,
    private val asset: AssetSelection,
    private val requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
) {
    suspend fun writeContentTo(destination: ByteWriteChannel): KoniferV2Result<Unit> =
        fetchAssetContentTo(infra, asset, requestedTransformation, destination)

    suspend fun contentBytes(): KoniferV2Result<ByteArray> =
        fetchAssetContentBytes(infra, asset, requestedTransformation)

    // link and redirect modes return the same delivery URL, so I am not exposing a redirect() method
    suspend fun link(): KoniferV2Result<AssetLinkResponse> =
        fetchAssetLink(infra, asset, requestedTransformation)
}
