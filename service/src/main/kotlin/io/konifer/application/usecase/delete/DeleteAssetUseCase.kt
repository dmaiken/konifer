package io.konifer.application.usecase.delete

import io.konifer.domain.context.DeleteRequestContext
import io.konifer.domain.ports.AssetDeleter
import io.konifer.domain.ports.DeleteAssetsCommand
import io.ktor.util.logging.KtorSimpleLogger

class DeleteAssetUseCase(
    private val assetDeleter: AssetDeleter,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun deleteAssets(context: DeleteRequestContext) {
        val command =
            if (context.modifiers.entryId != null) {
                logger.info("Deleting asset with path: ${context.path} and entryId: ${context.modifiers.entryId}")
                DeleteAssetsCommand.Entry(
                    path = context.path,
                    entryId = context.modifiers.entryId,
                )
            } else if (context.modifiers.recursive) {
                logger.info("Deleting assets recursively at path: ${context.path} with labels: ${context.labels}")
                DeleteAssetsCommand.Recursively(
                    path = context.path,
                    labels = context.labels,
                )
            } else {
                logger.info(
                    "Deleting assets at path: ${context.path} with labels: ${context.labels} ordering by: ${context.modifiers.order}" +
                        " and limit: ${context.modifiers.limit}",
                )
                DeleteAssetsCommand.AtPath(
                    path = context.path,
                    labels = context.labels,
                    order = context.modifiers.order,
                    limit = context.modifiers.limit,
                )
            }

        assetDeleter.delete(command)
    }
}
