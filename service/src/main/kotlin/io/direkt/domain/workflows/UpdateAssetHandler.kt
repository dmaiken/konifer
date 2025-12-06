package io.direkt.domain.workflows

import io.direkt.asset.handler.dto.UpdateAssetDto
import io.direkt.domain.asset.AssetAndLocation
import io.direkt.domain.asset.AssetNotFoundException
import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.service.context.UpdateRequestContext

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
                throw AssetNotFoundException(
                    e,
                    "Asset not found with path: ${context.path}, entryId: ${context.entryId}",
                )
            }

        return AssetAndLocation(updated, context.path)
    }
}
