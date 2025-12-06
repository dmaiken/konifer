package io.direkt.asset.handler

import io.direkt.domain.ports.AssetRepository
import io.direkt.path.DeleteMode
import io.ktor.util.logging.KtorSimpleLogger

class DeleteAssetHandler(
    private val assetRepository: AssetRepository,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun deleteAsset(
        uriPath: String,
        entryId: Long? = null,
    ) {
        if (entryId == null) {
            logger.info("Deleting asset with path: $uriPath")
        } else {
            logger.info("Deleting asset with path: $uriPath and entry id: $entryId")
        }
        assetRepository.deleteAssetByPath(uriPath, entryId)
    }

    suspend fun deleteAssets(
        uriPath: String,
        mode: DeleteMode,
    ) {
        when (mode) {
            DeleteMode.SINGLE -> throw IllegalArgumentException("Delete mode of: $mode not allowed")
            DeleteMode.CHILDREN -> logger.info("Deleting assets at path: $uriPath")
            DeleteMode.RECURSIVE -> logger.info("Deleting assets at path: $uriPath and all underneath it!")
        }
        assetRepository.deleteAssetsByPath(uriPath, mode == DeleteMode.RECURSIVE)
    }
}
