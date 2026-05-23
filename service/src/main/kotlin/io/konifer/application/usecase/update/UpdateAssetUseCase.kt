package io.konifer.application.usecase.update

import io.konifer.application.usecase.store.AssetAndLocation
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetNotFoundException
import io.konifer.domain.context.UpdateRequestContext
import io.konifer.domain.ports.AssetRepository
import java.lang.IllegalArgumentException

class UpdateAssetUseCase(
    private val assetRepository: AssetRepository,
) {
    suspend fun updateAsset(
        context: UpdateRequestContext,
        request: StoreAssetRequest,
    ): AssetAndLocation {
        val asset =
            assetRepository.fetchForUpdate(
                path = context.path,
                entryId = context.entryId,
            ) ?: throw AssetNotFoundException(message = "Asset not found with path: ${context.path}, entryId: ${context.entryId}")

        if (asset !is Asset.Ready) {
            throw IllegalArgumentException("Asset must be in ready state")
        }

        val updated =
            try {
                assetRepository.update(
                    asset =
                        asset.update(
                            alt = request.alt,
                            labels = request.labels,
                            tags = request.tags,
                        ),
                )
            } catch (e: IllegalStateException) {
                throw AssetNotFoundException(
                    e,
                    "Asset not found with path: ${context.path}, entryId: ${context.entryId}",
                )
            }

        return AssetAndLocation(updated, context.path)
    }
}
