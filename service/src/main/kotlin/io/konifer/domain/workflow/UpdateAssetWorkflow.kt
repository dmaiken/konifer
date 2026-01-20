package io.konifer.domain.workflow

import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetAndLocation
import io.konifer.domain.asset.AssetNotFoundException
import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.StoreAssetRequest
import io.konifer.service.context.UpdateRequestContext
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
