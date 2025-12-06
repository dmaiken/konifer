package io.direkt.asset.handler

import io.direkt.service.context.UpdateRequestContext
import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.repository.AssetRepository

class UpdateAssetHandler(
    private val assetRepository: AssetRepository,
) {
    suspend fun updateAsset(
        context: UpdateRequestContext,
        request: StoreAssetRequest,
    ): AssetAndLocation {
        request.validate()

        val updated =
            try {
                assetRepository.update(
                    UpdateAssetDto(
                        path = context.path,
                        entryId = context.entryId,
                        request = request,
                    ),
                )
            } catch (e: IllegalStateException) {
                throw AssetNotFoundException(e, "Asset not found with path: ${context.path}, entryId: ${context.entryId}")
            }

        return AssetAndLocation(updated, context.path)
    }
}
