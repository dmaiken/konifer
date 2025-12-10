package io.direkt.domain.workflows

import io.direkt.domain.asset.Asset
import io.direkt.domain.asset.AssetAndLocation
import io.direkt.domain.asset.AssetNotFoundException
import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.service.context.UpdateRequestContext
import java.lang.IllegalArgumentException

class UpdateAssetWorkflow(
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
