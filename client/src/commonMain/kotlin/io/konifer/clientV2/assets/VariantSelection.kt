package io.konifer.clientV2.assets

import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.RequestInfrastructure
import io.konifer.clientV2.assets.fetch.ContentDelivery
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
    suspend fun fetchAndWriteContentTo(destination: ByteWriteChannel): KoniferV2Result<Unit> =
        fetchAndWriteContentTo(destination, ContentDelivery.THROUGH_KONIFER)

    suspend fun fetchAndWriteContentTo(
        destination: ByteWriteChannel,
        delivery: ContentDelivery,
    ): KoniferV2Result<Unit> = fetchAssetContentTo(infra, asset, requestedTransformation, destination, delivery)

    suspend fun fetchContentBytes(): KoniferV2Result<ByteArray> = fetchContentBytes(ContentDelivery.THROUGH_KONIFER)

    suspend fun fetchContentBytes(delivery: ContentDelivery): KoniferV2Result<ByteArray> =
        fetchAssetContentBytes(infra, asset, requestedTransformation, delivery)

    // link and redirect modes return the same delivery URL, so I am not exposing a redirect() method
    suspend fun fetchLink(): KoniferV2Result<AssetLinkResponse> = fetchAssetLink(infra, asset, requestedTransformation)
}
