package io.konifer.domain.workflow

import io.konifer.domain.ports.AssetRepository
import io.konifer.service.context.DeleteRequestContext
import io.ktor.util.logging.KtorSimpleLogger

class DeleteAssetWorkflow(
    private val assetRepository: AssetRepository,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun deleteAssets(context: DeleteRequestContext) {
        if (context.modifiers.entryId != null) {
            logger.info("Deleting asset with path: ${context.path} and entryId: ${context.modifiers.entryId}")
            assetRepository.deleteByPath(
                path = context.path,
                entryId = context.modifiers.entryId,
            )
        } else if (context.modifiers.recursive) {
            logger.info("Deleting assets recursively at path: ${context.path} with labels: ${context.labels}")
            assetRepository.deleteRecursivelyByPath(
                path = context.path,
                labels = context.labels,
            )
        } else {
            logger.info(
                "Deleting assets at path: ${context.path} with labels: ${context.labels} ordering by: ${context.modifiers.order}" +
                    " and limit: ${context.modifiers.limit}",
            )
            assetRepository.deleteAllByPath(
                path = context.path,
                labels = context.labels,
                order = context.modifiers.order,
                limit = context.modifiers.limit,
            )
        }
    }
}
