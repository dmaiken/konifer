package io.asset.handler

import io.asset.model.StoreAssetRequest
import io.asset.repository.AssetRepository

class UpdateAssetHandler(
    private val assetRepository: AssetRepository,
) {
    suspend fun updateAsset(
        uriPath: String,
        entryId: Long,
        request: StoreAssetRequest,
    ): AssetAndLocation {
        request.validate()

        val updated =
            try {
                assetRepository.update(
                    UpdateAssetDto(
                        path = uriPath,
                        entryId = entryId,
                        request = request,
                    ),
                )
            } catch (e: IllegalStateException) {
                throw AssetNotFoundException(e, "Asset not found with path: $uriPath, entryId: $entryId")
            }

        return AssetAndLocation(updated, uriPath)
    }
}
